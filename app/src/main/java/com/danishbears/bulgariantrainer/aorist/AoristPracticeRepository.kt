package com.danishbears.bulgariantrainer.aorist

import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.review.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

class AoristPracticeRepository internal constructor(private val db:TrainerDatabase,private val clock:Clock=Clock.systemUTC(),random:Random=Random.Default) {
    private val dao=db.aoristPracticeDao()
    private val content=AoristRepository(db,clock)
    private val scheduler=ReviewScheduler(clock)
    private val planner=AoristPlanner(random)
    private val choices=AoristChoicePlanner(random)
    private data class Pool(val stored:List<AoristStoredTarget>,val candidates:List<AoristCandidate>,val unavailable:Int)
    private fun AoristScheduleEntity.schedule()=ReviewSchedule(attempts,successes,streak,intervalDays,Instant.ofEpochMilli(lastReview),Instant.ofEpochMilli(nextReview))
    private suspend fun pool(s:AoristSettings):Pool {
        val targets=dao.targets(s.datasetId,s.aspect,s.studyGroup)
        val entries=targets.map{it.form.verbId}.distinct().associateWith{content.get(it).entry}
        val stored=targets.map{AoristStoredTarget(it.form.id,it.form.verbId,it.form.person,entries.getValue(it.form.verbId))}
        val schedules=dao.schedules().filter{it.exercise==s.exercise.name}.associateBy{it.formId}
        val now=clock.instant();var unavailable=0
        val candidates=stored.filter{it.person in s.persons}.mapNotNull { target->
            val category=scheduler.category(schedules[target.formId]?.schedule(),now)
            if(!scheduler.eligible(category,s.scope))return@mapNotNull null
            val count=if(s.exercise==AoristExercise.CHOICE)choices.count(target,stored)else 0
            if(s.exercise==AoristExercise.CHOICE&&count<2){unavailable++;null}
            else AoristCandidate(target,scheduler.priority(category),count)
        }
        return Pool(stored,candidates,unavailable)
    }
    suspend fun preferences():AoristSettings=db.withContentTransaction{dao.preferences()?.let{AoristSettings.read(it.settings)}?:AoristSettings()}
    suspend fun availability(settings:AoristSettings):AoristAvailability=db.withContentTransaction {
        val s=settings.canonical();val p=pool(s)
        AoristAvailability(p.candidates.size,minOf(s.size,p.candidates.size),p.candidates.size<s.size,
            p.candidates.count{it.optionCount in 2..3},p.unavailable)
    }
    suspend fun get(id:Long):AoristPracticeState=db.withContentTransaction{state(graph(id))}
    fun observe(id:Long):Flow<AoristPracticeState?> = dao.observe(id).map{it?.let(::state)}.flowOn(Dispatchers.Default)
    suspend fun restore(preferred:Long?=null):AoristPracticeState?=db.withContentTransaction {
        val requested=preferred?.let{dao.graph(it)}
        if(requested?.session?.status==AoristSessionStatus.IN_PROGRESS.name)return@withContentTransaction state(requested)
        val latest=dao.latest()?.let{dao.graph(it)}
        if(latest?.session?.status==AoristSessionStatus.IN_PROGRESS.name)state(latest)else(requested?:latest)?.let(::state)
    }
    suspend fun start(requestId:String,settings:AoristSettings=AoristSettings(),allowShorter:Boolean=false,allowFewerOptions:Boolean=false):AoristPracticeState {
        val request=try{UUID.fromString(requestId).toString()}catch(_:Exception){throw AoristException("Некорректный запрос занятия.")}
        val s=settings.canonical()
        return db.withContentTransaction {
            dao.request(request)?.let{id->val previous=graph(id)
                if(AoristSettings.read(previous.session.settings)!=s||previous.session.allowShorter!=allowShorter||previous.session.allowFewerOptions!=allowFewerOptions)
                    throw AoristException("Повторный запуск имеет другие параметры. Начните новый запрос.")
                return@withContentTransaction state(previous)
            }
            val p=pool(s)
            if(p.candidates.isEmpty())throw AoristException(if(s.exercise==AoristExercise.CHOICE)"Нет подходящих заданий с разными вариантами. Выберите набор ответа или другую очередь." else "В выбранной очереди нет подходящих форм аориста.")
            if(p.candidates.size<s.size&&!allowShorter)throw AoristException("Форм меньше запрошенного размера. Подтвердите короткое занятие.")
            val plan=planner.plan(p.candidates,s.size)
            if(plan.any{it.optionCount in 2..3}&&!allowFewerOptions)throw AoristException("Для части заданий есть только 2–3 варианта. Подтвердите это или выберите набор ответа.")
            val id=dao.insertSession(AoristSessionEntity(requestId=request,settings=s.json(),allowShorter=allowShorter,
                allowFewerOptions=allowFewerOptions,total=plan.size,startedAt=clock.millis()))
            for((i,candidate) in plan.withIndex()) {
                val t=candidate.target;val options=if(s.exercise==AoristExercise.CHOICE)choices.options(t,p.stored)else emptyList()
                dao.insertQuestion(AoristQuestionEntity(sessionId=id,formId=t.formId,verbId=t.verbId,person=t.person,exercise=s.exercise.name,
                    position=i+1,snapshot=t.content.toString(),contentHash=AoristContent.hash(t.content),options=optionsJson(options)))
            }
            dao.savePreferences(AoristPreferencesEntity(settings=s.json()))
            state(graph(id))
        }
    }
    suspend fun submitTyped(sessionId:Long,questionId:Long,raw:String):AoristPracticeState=submit(sessionId,questionId,raw,null)
    suspend fun submitChoice(sessionId:Long,questionId:Long,optionId:String):AoristPracticeState=submit(sessionId,questionId,null,optionId)
    private suspend fun submit(sessionId:Long,questionId:Long,raw:String?,optionId:String?):AoristPracticeState=db.withContentTransaction {
        val g=graph(sessionId);val item=question(g,questionId);val q=item.question
        val exercise=AoristExercise.valueOf(q.exercise)
        if(exercise==AoristExercise.TYPED&&raw==null||exercise==AoristExercise.CHOICE&&optionId==null)throw AoristException("Тип ответа не соответствует заданию.")
        item.attempt?.let{
            if(it.outcome==AoristOutcome.REVEALED.name)throw AoristException("Ответ раскрыт: самостоятельный ответ не засчитывается.")
            if(exercise==AoristExercise.TYPED&&it.submitted!=raw||exercise==AoristExercise.CHOICE&&it.optionId!=optionId)throw AoristException("Для этого задания уже сохранён другой ответ.")
            return@withContentTransaction state(g)
        }
        current(g.session,q)
        val submitted=if(exercise==AoristExercise.TYPED)raw!! else readOptions(q.options).singleOrNull{it.id==optionId}?.text
            ?:throw AoristException("Вариант не относится к этому заданию.")
        AoristContent.requireUnicode(submitted)
        val key=AoristContent.key(submitted)
        if(submitted.length>4000||key.isEmpty())throw AoristException("Введите непустой ответ до 4000 знаков.")
        val correct=accepted(q).any{AoristContent.key(it)==key}
        record(g.session,q,submitted,optionId,if(correct)AoristOutcome.CORRECT else AoristOutcome.INCORRECT)
        state(graph(sessionId))
    }
    suspend fun reveal(sessionId:Long,questionId:Long):AoristPracticeState=db.withContentTransaction {
        val g=graph(sessionId);val q=question(g,questionId)
        q.attempt?.let{if(it.outcome==AoristOutcome.REVEALED.name)return@withContentTransaction state(g)
            throw AoristException("Ответ уже отправлен; результат сохранён.")}
        current(g.session,q.question);record(g.session,q.question,null,null,AoristOutcome.REVEALED);state(graph(sessionId))
    }
    private suspend fun record(session:AoristSessionEntity,q:AoristQuestionEntity,raw:String?,option:String?,outcome:AoristOutcome) {
        val at=clock.millis();val correct=outcome==AoristOutcome.CORRECT
        dao.insertAttempt(AoristAttemptEntity(questionId=q.id,submitted=raw,optionId=option,normalized=raw?.let(AoristContent::key),outcome=outcome.name,createdAt=at))
        val p=dao.progress(q.formId,q.exercise)
        dao.saveProgress(AoristProgressEntity(q.formId,q.exercise,(p?.attempts?:0)+1,(p?.correct?:0)+if(correct)1 else 0,
            (p?.incorrect?:0)+if(outcome==AoristOutcome.INCORRECT)1 else 0,(p?.revealed?:0)+if(outcome==AoristOutcome.REVEALED)1 else 0,at))
        val schedule=scheduler.record(dao.schedule(q.formId,q.exercise)?.schedule(),correct,Instant.ofEpochMilli(at))
        dao.saveSchedule(AoristScheduleEntity(q.formId,q.exercise,schedule.attempts,schedule.successes,schedule.streak,schedule.intervalDays,at,schedule.nextReview!!.toEpochMilli()))
        dao.updateQuestion(q.copy(draft=raw.orEmpty(),selectedDraft=option,draftVersion=q.draftVersion+1,revealedAt=if(outcome==AoristOutcome.REVEALED)at else null))
        // Feedback remains current until an explicit Next. Last-item feedback is recoverable too.
        dao.updateSession(session.copy(revision=session.revision+1))
    }
    suspend fun saveDraft(sessionId:Long,questionId:Long,raw:String,optionId:String?,version:Long)=db.withContentTransaction {
        val g=graph(sessionId);val item=question(g,questionId);val q=item.question
        if(item.attempt!=null||q.acknowledged||g.session.status!=AoristSessionStatus.IN_PROGRESS.name||g.session.position!=q.position)return@withContentTransaction
        AoristContent.requireUnicode(raw)
        if(raw.length>4000||version<1)throw AoristException("Некорректный черновик.")
        if(q.exercise==AoristExercise.TYPED.name&&optionId!=null||q.exercise==AoristExercise.CHOICE.name&&(raw.isNotEmpty()||optionId!=null&&readOptions(q.options).none{it.id==optionId}))throw AoristException("Черновик не соответствует заданию.")
        if(version<q.draftVersion)return@withContentTransaction
        if(version==q.draftVersion){if(raw!=q.draft||optionId!=q.selectedDraft)throw AoristException("Уже сохранён другой черновик. Перечитайте задание.");return@withContentTransaction}
        dao.updateQuestion(q.copy(draft=raw,selectedDraft=optionId,draftVersion=version))
    }
    suspend fun next(sessionId:Long,questionId:Long):AoristPracticeState=db.withContentTransaction {
        val g=graph(sessionId);val q=question(g,questionId)
        if(q.question.acknowledged)return@withContentTransaction state(g)
        current(g.session,q.question)
        if(q.attempt==null)throw AoristException("Сначала отправьте или раскройте ответ.")
        val position=g.session.position+1;val done=position>g.session.total
        dao.updateQuestion(q.question.copy(acknowledged=true))
        dao.updateSession(g.session.copy(position=position,revision=g.session.revision+1,status=if(done)AoristSessionStatus.COMPLETED.name else g.session.status,completedAt=if(done)clock.millis()else null))
        state(graph(sessionId))
    }
    suspend fun complete(sessionId:Long):AoristPracticeState=db.withContentTransaction {
        val g=graph(sessionId);val s=g.session
        if(s.status!=AoristSessionStatus.IN_PROGRESS.name)return@withContentTransaction state(g)
        val count=g.questions.count{it.attempt!=null}
        for(q in g.questions.filter{it.attempt!=null&&!it.question.acknowledged})dao.updateQuestion(q.question.copy(acknowledged=true))
        dao.updateSession(s.copy(position=s.total+1,revision=s.revision+1,status=if(count==s.total)AoristSessionStatus.COMPLETED.name else AoristSessionStatus.ABANDONED.name,completedAt=clock.millis()))
        state(graph(sessionId))
    }
    suspend fun progress(verbId:Long,person:String,exercise:AoristExercise):AoristProgress?=db.withContentTransaction {
        if(person !in AoristContent.persons)throw AoristException("Некорректное лицо.")
        val form=db.aoristDao().forms(verbId).singleOrNull{it.person==person}?:return@withContentTransaction null
        val p=dao.progress(form.id,exercise.name)?:return@withContentTransaction null
        val schedule=dao.schedule(form.id,exercise.name)?.schedule()?:error("Missing schedule")
        AoristProgress(verbId,person,exercise,p.attempts,p.correct,p.incorrect,p.revealed,scheduler.category(schedule),schedule)
    }
    private suspend fun graph(id:Long)=dao.graph(id)?:throw AoristException("Занятие аориста не найдено.")
    private fun question(g:AoristSessionGraph,id:Long)=g.questions.singleOrNull{it.question.id==id}?:throw AoristException("Задание не относится к этому занятию.")
    private fun current(s:AoristSessionEntity,q:AoristQuestionEntity) {
        if(s.status!=AoristSessionStatus.IN_PROGRESS.name)throw AoristException("Занятие завершено.")
        if(s.position!=q.position||q.acknowledged)throw AoristException("Это задание больше не текущее.")
    }
    private fun snapshot(q:AoristQuestionEntity)=AoristContent.strict(q.snapshot).jsonObject
    private fun accepted(q:AoristQuestionEntity)=AoristContent.answers(snapshot(q),q.person)
    private fun prompt(q:AoristQuestionEntity):AoristPrompt {val e=snapshot(q);return AoristPrompt(AoristContent.text(e,"lemma"),AoristContent.text(e,"russian"),q.person)}
    private fun feedback(q:AoristQuestionGraph)=q.attempt?.let{AoristFeedback(AoristOutcome.valueOf(it.outcome),it.submitted,it.optionId,accepted(q.question),snapshot(q.question))}
    private fun state(g:AoristSessionGraph):AoristPracticeState {
        val s=g.session;val ordered=g.questions.sortedBy{it.question.position};val status=AoristSessionStatus.valueOf(s.status)
        val all=ordered.mapNotNull{it.attempt};val correct=all.count{it.outcome==AoristOutcome.CORRECT.name};val incorrect=all.count{it.outcome==AoristOutcome.INCORRECT.name}
        val q=if(status==AoristSessionStatus.IN_PROGRESS)ordered.singleOrNull{it.question.position==s.position&&!it.question.acknowledged}else null
        return AoristPracticeState(s.id,AoristSettings.read(s.settings),status,s.total,s.position,s.revision,correct+incorrect,correct,incorrect,all.count{it.outcome==AoristOutcome.REVEALED.name},
            q?.let{val item=it.question;AoristQuestion(item.id,item.verbId,item.formId,item.position,AoristExercise.valueOf(item.exercise),prompt(item),readOptions(item.options),item.draft,item.selectedDraft,item.draftVersion,feedback(it))},
            if(status==AoristSessionStatus.IN_PROGRESS)emptyList()else ordered.map{AoristReview(it.question.position,AoristExercise.valueOf(it.question.exercise),prompt(it.question),feedback(it))})
    }
    /** Validate historical facts and frozen content, without replaying progress or regrading outcomes. */
    internal suspend fun validateStored() {
        dao.preferences()?.let{require(it.id==1);AoristSettings.read(it.settings)}
        val totals=mutableMapOf<Pair<Long,String>,IntArray>()
        val last=mutableMapOf<Pair<Long,String>,Pair<Long,Long>>()
        for(id in dao.allIds()) {
            val g=graph(id);val s=g.session;val settings=AoristSettings.read(s.settings);val status=AoristSessionStatus.valueOf(s.status)
            require(s.id>0&&s.policyVersion==1&&UUID.fromString(s.requestId).toString()==s.requestId&&s.revision>=0)
            require(s.total in 1..settings.size&&(s.total==settings.size||s.allowShorter)&&s.position in 1..s.total+1)
            require((s.completedAt==null)==(status==AoristSessionStatus.IN_PROGRESS))
            val ordered=g.questions.sortedBy{it.question.position};require(ordered.map{it.question.position}==(1..s.total).toList())
            val count=ordered.count{it.attempt!=null}
            require(ordered.take(count).all{it.attempt!=null}&&ordered.drop(count).all{it.attempt==null})
            when(status) {
                AoristSessionStatus.IN_PROGRESS->require(s.position<=s.total&&count in (s.position-1)..s.position)
                AoristSessionStatus.COMPLETED->require(count==s.total&&s.position==s.total+1)
                AoristSessionStatus.ABANDONED->require(count<s.total&&s.position==s.total+1)
            }
            for(item in ordered) {
                val q=item.question;val e=snapshot(q);val form=dao.form(q.formId)?:error("Missing target")
                require(q.id>0&&form.verbId==q.verbId&&form.person==q.person&&q.person in settings.persons&&q.exercise==settings.exercise.name)
                require(AoristContent.validate(e).isEmpty()&&q.contentHash==AoristContent.hash(e))
                require(AoristContent.text(e,"id")==AoristContent.text(content.get(q.verbId).entry,"id"))
                settings.aspect?.let{require(e["aspect"]==JsonPrimitive(it))};settings.studyGroup?.let{require(e["studyGroup"]==JsonPrimitive(it))}
                settings.datasetId?.let{require(content.get(q.verbId).datasetId==it)}
                require(q.draft.length<=4000&&q.draftVersion>=0&&q.acknowledged==(item.attempt!=null&&q.position<s.position))
                val options=readOptions(q.options)
                if(settings.exercise==AoristExercise.TYPED)require(options.isEmpty()&&q.selectedDraft==null)
                else {
                    require(options.size in 2..4&&(options.size==4||s.allowFewerOptions))
                    require(options.map{it.id}==options.indices.map{"o${it+1}"})
                    val keys=options.map{require(it.text.isNotBlank()&&it.text.length<=255);AoristContent.key(it.text)}
                    require(keys.distinct().size==keys.size&&keys.count{it in accepted(q).map(AoristContent::key).toSet()}==1)
                    require(q.draft.isEmpty()||item.attempt!=null)
                    require(q.selectedDraft==null||options.any{it.id==q.selectedDraft})
                }
                item.attempt?.let{a->
                    val outcome=AoristOutcome.valueOf(a.outcome);val key=q.formId to q.exercise;val counts=totals.getOrPut(key){IntArray(4)}
                    counts[0]++;when(outcome){AoristOutcome.CORRECT->counts[1]++;AoristOutcome.INCORRECT->counts[2]++;AoristOutcome.REVEALED->counts[3]++}
                    if(a.id>(last[key]?.first?:0))last[key]=a.id to a.createdAt
                    if(outcome==AoristOutcome.REVEALED)require(a.submitted==null&&a.normalized==null&&a.optionId==null&&q.revealedAt==a.createdAt&&q.draft.isEmpty()&&q.selectedDraft==null)
                    else {
                        require(q.revealedAt==null&&a.submitted!=null&&a.submitted.length<=4000&&AoristContent.key(a.submitted).isNotEmpty()&&a.normalized==AoristContent.key(a.submitted)&&q.draft==a.submitted)
                        if(settings.exercise==AoristExercise.TYPED)require(a.optionId==null)else require(options.singleOrNull{it.id==a.optionId}?.text==a.submitted&&q.selectedDraft==a.optionId)
                    }
                }?:require(q.revealedAt==null)
            }
            state(g)
        }
        val rows=dao.progressRows();require(rows.map{it.formId to it.exercise}.toSet()==totals.keys)
        for(p in rows){val counts=totals.getValue(p.formId to p.exercise)
            require(p.attempts==counts[0]&&p.correct==counts[1]&&p.incorrect==counts[2]&&p.revealed==counts[3]&&p.lastReviewedAt==last.getValue(p.formId to p.exercise).second)
            require(dao.schedule(p.formId,p.exercise)!=null)}
        for(s in dao.schedules()) {
            val p=dao.progress(s.formId,s.exercise)?:error("Missing progress")
            require(s.exercise in AoristExercise.entries.map{it.name}&&p.attempts>0&&s.attempts==p.attempts&&s.successes==p.correct&&s.streak in 0..s.successes&&s.intervalDays in 1..365&&s.lastReview==p.lastReviewedAt)
            require(s.nextReview==Instant.ofEpochMilli(s.lastReview).plus(Duration.ofDays(s.intervalDays.toLong())).toEpochMilli())
        }
    }
}
