package com.danishbears.bulgariantrainer.aspect

import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.data.withContentTransaction as withTransaction
import com.danishbears.bulgariantrainer.review.*
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

class AspectPracticeRepository internal constructor(private val db:TrainerDatabase,private val clock:Clock=Clock.systemUTC(),random:Random=Random.Default) {
    private val dao=db.aspectPracticeDao()
    private val content=AspectRepository(db,clock)
    private val scheduler=ReviewScheduler(clock)
    private val planner=AspectPlanner(random)
    private fun AspectScheduleEntity.schedule()=ReviewSchedule(attempts,successes,streak,intervalDays,Instant.ofEpochMilli(lastReview),Instant.ofEpochMilli(nextReview))
    private suspend fun candidates(settings:AspectSettings):List<AspectCandidate> {
        val schedules=dao.schedules().associateBy{it.pairId to it.direction};val at=clock.instant()
        fun priority(id:Long,direction:AspectDirection):Int? {
            if(settings.direction!=AspectDirection.MIXED&&settings.direction!=direction)return null
            val category=scheduler.category(schedules[id to direction.name]?.schedule(),at)
            return if(scheduler.eligible(category,settings.scope))scheduler.priority(category) else null
        }
        return dao.candidates(settings.stages,settings.topic,settings.namespace).mapNotNull { id ->
            val f=priority(id,AspectDirection.IMPERFECTIVE_TO_PERFECTIVE);val r=priority(id,AspectDirection.PERFECTIVE_TO_IMPERFECTIVE)
            if(f==null&&r==null)null else AspectCandidate(id,f,r)
        }
    }
    suspend fun availability(settings:AspectSettings):AspectAvailability=db.withTransaction {
        val s=settings.canonical();val pool=candidates(s)
        AspectAvailability(pool.size,pool.count{it.forward!=null},pool.count{it.reverse!=null},minOf(s.size,pool.size),pool.size<s.size)
    }
    suspend fun get(id:Long):AspectPracticeState=db.withTransaction{state(graph(id))}
    fun observe(id:Long):Flow<AspectPracticeState?> = dao.observe(id).map{it?.let(::state)}.flowOn(Dispatchers.Default)
    suspend fun restore(preferred:Long?=null):AspectPracticeState?=db.withTransaction {
        val requested=preferred?.let{dao.graph(it)}
        if(requested?.session?.status==AspectSessionStatus.IN_PROGRESS.name)return@withTransaction state(requested)
        val latest=dao.latest()?.let{dao.graph(it)}
        if(latest?.session?.status==AspectSessionStatus.IN_PROGRESS.name)state(latest) else (requested?:latest)?.let(::state)
    }
    suspend fun start(requestId:String,settings:AspectSettings=AspectSettings(),allowShorter:Boolean=false):AspectPracticeState {
        val request=try{UUID.fromString(requestId).toString()}catch(_:Exception){throw AspectException("Некорректный запрос занятия.")}
        val s=settings.canonical()
        return db.withTransaction {
            dao.request(request)?.let { id ->
                val old=graph(id)
                if(AspectSettings.read(old.session.settings)!=s||old.session.allowShorter!=allowShorter)throw AspectException("Параметры повторного запуска изменены. Начните новый запрос.")
                return@withTransaction state(old)
            }
            val pool=candidates(s)
            if(pool.isEmpty())throw AspectException("В выбранной очереди нет подходящих видовых пар.")
            if(pool.size<s.size&&!allowShorter)throw AspectException("Пар меньше запрошенного размера. Подтвердите короткое занятие.")
            val plan=planner.plan(pool,s.size,s.direction)
            val id=dao.insertSession(AspectSessionEntity(requestId=request,settings=s.json(),allowShorter=allowShorter,total=plan.size,startedAt=clock.millis()))
            for((i,item) in plan.withIndex()) {
                val entry=content.get(item.id).entry
                dao.insertQuestion(AspectQuestionEntity(sessionId=id,pairId=item.id,position=i+1,direction=item.direction.name,snapshot=entry.toString(),contentHash=AspectContent.hash(entry)))
            }
            state(graph(id))
        }
    }
    suspend fun submit(sessionId:Long,questionId:Long,submitted:String):AspectPracticeState=db.withTransaction {
        val graph=graph(sessionId);val q=question(graph,questionId)
        q.attempt?.let {
            if(it.outcome==AspectOutcome.REVEALED.name)throw AspectException("Ответ уже раскрыт: самостоятельный ответ не засчитывается.")
            if(it.submitted!=submitted)throw AspectException("Для этой карточки уже сохранён другой ответ.")
            return@withTransaction state(graph)
        }
        current(graph.session,q.question)
        val key=AspectContent.key(submitted)
        if(submitted.length>4000||key.isEmpty())throw AspectException("Введите непустой ответ до 4000 знаков.")
        val correct=accepted(q.question).any{AspectContent.key(it)==key}
        record(graph.session,q.question,submitted,if(correct)AspectOutcome.CORRECT else AspectOutcome.INCORRECT)
        state(graph(sessionId))
    }
    suspend fun reveal(sessionId:Long,questionId:Long):AspectPracticeState=db.withTransaction {
        val graph=graph(sessionId);val q=question(graph,questionId)
        q.attempt?.let {
            if(it.outcome==AspectOutcome.REVEALED.name)return@withTransaction state(graph)
            throw AspectException("Ответ уже отправлен; его результат показан.")
        }
        current(graph.session,q.question);record(graph.session,q.question,null,AspectOutcome.REVEALED)
        state(graph(sessionId))
    }
    private suspend fun record(session:AspectSessionEntity,q:AspectQuestionEntity,raw:String?,outcome:AspectOutcome) {
        val at=clock.millis();val correct=outcome==AspectOutcome.CORRECT
        dao.insertAttempt(AspectAttemptEntity(questionId=q.id,submitted=raw,normalized=raw?.let(AspectContent::key),outcome=outcome.name,createdAt=at))
        val p=dao.progress(q.pairId,q.direction)
        dao.saveProgress(AspectProgressEntity(q.pairId,q.direction,(p?.attempts?:0)+1,(p?.correct?:0)+if(correct)1 else 0,
            (p?.revealed?:0)+if(outcome==AspectOutcome.REVEALED)1 else 0,at))
        val schedule=scheduler.record(dao.schedule(q.pairId,q.direction)?.schedule(),correct,Instant.ofEpochMilli(at))
        dao.saveSchedule(AspectScheduleEntity(q.pairId,q.direction,schedule.attempts,schedule.successes,schedule.streak,schedule.intervalDays,at,schedule.nextReview!!.toEpochMilli()))
        if(outcome==AspectOutcome.REVEALED)dao.updateQuestion(q.copy(revealedAt=at))
        val last=q.position==session.total
        dao.updateSession(session.copy(revision=session.revision+1,status=if(last)AspectSessionStatus.COMPLETED.name else session.status,completedAt=if(last)at else session.completedAt))
    }
    suspend fun next(sessionId:Long,questionId:Long):AspectPracticeState=db.withTransaction {
        val graph=graph(sessionId);val q=question(graph,questionId)
        if(q.question.acknowledged)return@withTransaction state(graph)
        if(graph.session.status==AspectSessionStatus.ABANDONED.name)throw AspectException("Занятие уже завершено.")
        if(q.question.position!=graph.session.position)throw AspectException("Эта карточка больше не текущая.")
        if(q.attempt==null)throw AspectException("Сначала отправьте или раскройте ответ.")
        dao.updateQuestion(q.question.copy(acknowledged=true))
        dao.updateSession(graph.session.copy(position=graph.session.position+1,revision=graph.session.revision+1))
        state(graph(sessionId))
    }
    suspend fun complete(sessionId:Long):AspectPracticeState=db.withTransaction {
        val graph=graph(sessionId);val s=graph.session
        if(s.position<=s.total) {
            for(q in graph.questions.filter{it.attempt!=null&&!it.question.acknowledged})dao.updateQuestion(q.question.copy(acknowledged=true))
            dao.updateSession(s.copy(position=s.total+1,revision=s.revision+1,status=if(s.status==AspectSessionStatus.IN_PROGRESS.name)AspectSessionStatus.ABANDONED.name else s.status,completedAt=s.completedAt?:clock.millis()))
        }
        state(graph(sessionId))
    }
    private suspend fun graph(id:Long)=dao.graph(id)?:throw AspectException("Занятие не найдено.")
    private fun question(graph:AspectSessionGraph,id:Long)=graph.questions.find{it.question.id==id}?:throw AspectException("Карточка не относится к этому занятию.")
    private fun current(session:AspectSessionEntity,q:AspectQuestionEntity) {
        if(session.status!=AspectSessionStatus.IN_PROGRESS.name)throw AspectException("Занятие уже завершено.")
        if(session.position!=q.position||q.acknowledged)throw AspectException("Эта карточка больше не текущая.")
    }
    private fun snapshot(q:AspectQuestionEntity)=AspectContent.strict(q.snapshot).jsonObject
    private fun accepted(q:AspectQuestionEntity)=AspectContent.answers(snapshot(q),if(q.direction==AspectDirection.IMPERFECTIVE_TO_PERFECTIVE.name)"acceptedPerfective" else "acceptedImperfective")
    private fun prompt(q:AspectQuestionEntity):AspectPrompt {
        val e=snapshot(q);val f=q.direction==AspectDirection.IMPERFECTIVE_TO_PERFECTIVE.name
        return AspectPrompt(AspectContent.string(e,if(f)"imperfective" else "perfective"),AspectContent.string(e,if(f)"russianImperfective" else "russianPerfective"),AspectContent.string(e,if(f)"russianPerfective" else "russianImperfective"))
    }
    private fun feedback(q:AspectQuestionGraph)=q.attempt?.let{AspectFeedback(AspectOutcome.valueOf(it.outcome),it.submitted,accepted(q.question),snapshot(q.question))}
    private fun state(graph:AspectSessionGraph):AspectPracticeState {
        val s=graph.session;val queue=graph.questions.sortedBy{it.question.position};val status=AspectSessionStatus.valueOf(s.status)
        val typed=queue.mapNotNull{it.attempt}.filter{it.outcome!=AspectOutcome.REVEALED.name}
        val current=if(status==AspectSessionStatus.ABANDONED)null else queue.find{it.question.position==s.position&&!it.question.acknowledged}
        return AspectPracticeState(s.id,AspectSettings.read(s.settings),status,s.total,s.position,s.revision,typed.size,typed.count{it.outcome==AspectOutcome.CORRECT.name},
            typed.count{it.outcome==AspectOutcome.INCORRECT.name},queue.count{it.attempt?.outcome==AspectOutcome.REVEALED.name},
            current?.let{AspectQuestion(it.question.id,it.question.position,AspectDirection.valueOf(it.question.direction),prompt(it.question),feedback(it))},
            if(status==AspectSessionStatus.IN_PROGRESS)emptyList() else queue.map{AspectReview(it.question.position,AspectDirection.valueOf(it.question.direction),prompt(it.question),feedback(it))})
    }
    internal suspend fun validateStored() {
        val totals=mutableMapOf<Pair<Long,String>,IntArray>()
        for(id in dao.allIds()) {
            val g=graph(id);val s=g.session;val settings=AspectSettings.read(s.settings)
            require(UUID.fromString(s.requestId).toString()==s.requestId&&s.total in 1..settings.size&&s.position in 1..s.total+1&&s.revision>=0)
            require(s.total==settings.size||s.allowShorter)
            val status=AspectSessionStatus.valueOf(s.status)
            require((s.completedAt==null)==(status==AspectSessionStatus.IN_PROGRESS))
            // Offline device-clock corrections may move time backwards; preserve original instants.
            require(g.questions.map{it.question.position}.sorted()==(1..s.total).toList())
            val answered=g.questions.count{it.attempt!=null}
            when(status) {
                AspectSessionStatus.COMPLETED->require(answered==s.total&&s.position>=s.total)
                AspectSessionStatus.ABANDONED->require(answered<s.total&&s.position==s.total+1)
                AspectSessionStatus.IN_PROGRESS->require(s.position<=s.total&&answered<s.total)
            }
            val ordered=g.questions.sortedBy{it.question.position}
            require(ordered.take(answered).all{it.attempt!=null}&&ordered.drop(answered).all{it.attempt==null})
            for(item in g.questions) {
                val q=item.question;val e=snapshot(q)
                require(q.direction in AspectDirection.entries.filter{it!=AspectDirection.MIXED}.map{it.name})
                require(settings.direction==AspectDirection.MIXED||q.direction==settings.direction.name)
                require(AspectContent.validate(e).isEmpty()&&q.contentHash==AspectContent.hash(e))
                require(!q.acknowledged||item.attempt!=null)
                require(q.acknowledged==(item.attempt!=null&&q.position<s.position))
                require(e.getValue("studyStage").jsonPrimitive.int in settings.stages)
                item.attempt?.let{a->
                    val outcome=AspectOutcome.valueOf(a.outcome)
                    val counters=totals.getOrPut(q.pairId to q.direction){IntArray(3)}
                    counters[0]++;if(outcome==AspectOutcome.CORRECT)counters[1]++;if(outcome==AspectOutcome.REVEALED)counters[2]++
                    if(outcome==AspectOutcome.REVEALED)require(a.submitted==null&&a.normalized==null&&q.revealedAt==a.createdAt)
                    else require(q.revealedAt==null&&a.submitted!=null&&a.submitted.length<=4000&&AspectContent.key(a.submitted).isNotEmpty()&&a.normalized==AspectContent.key(a.submitted))
                }?:require(q.revealedAt==null)
            }
            state(g) // Validate every historical graph without regrading/replaying its saved outcomes.
        }
        val progress=dao.progressRows()
        require(progress.map{it.pairId to it.direction}.toSet()==totals.keys)
        for(p in progress) {
            val counts=totals.getValue(p.pairId to p.direction)
            require(p.attempts==counts[0]&&p.correct==counts[1]&&p.revealed==counts[2])
            require(dao.schedule(p.pairId,p.direction)!=null)
        }
        for(s in dao.schedules()) {
            val p=dao.progress(s.pairId,s.direction)?:error("Missing aspect progress")
            require(s.direction in AspectDirection.entries.filter{it!=AspectDirection.MIXED}.map{it.name}&&p.attempts>0&&p.correct>=0&&p.revealed>=0&&p.correct+p.revealed<=p.attempts)
            require(s.attempts==p.attempts&&s.successes==p.correct&&s.streak in 0..s.successes&&s.intervalDays in 1..365&&s.lastReview==p.lastReviewedAt)
            require(s.nextReview==Instant.ofEpochMilli(s.lastReview).plus(java.time.Duration.ofDays(s.intervalDays.toLong())).toEpochMilli())
        }
    }
}
