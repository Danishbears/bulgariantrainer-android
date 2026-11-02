package com.danishbears.bulgariantrainer.pronunciation

import com.danishbears.bulgariantrainer.flashcards.*
import com.danishbears.bulgariantrainer.typing.*
import com.danishbears.bulgariantrainer.sentences.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

internal class FakeTtsAdapter : TtsAdapter {
    var initialized: ((Boolean) -> Unit)? = null
    var event: ((String, SpeechEvent) -> Unit)? = null
    var availability = LanguageAvailability.AVAILABLE
    var available = listOf(BulgarianVoice("local-bg", "bg-BG", false))
    var selected: BulgarianVoice? = null
    var rejected = emptySet<String>()
    var stopSuccess = true
    var speakSuccess = true
    var throwSpeaking = false
    var stopCount = 0
    var shutdownCount = 0
    data class Spoken(val text: String, val rate: Float, val id: String)
    val spoken = mutableListOf<Spoken>()
    override fun initialize(onInitialized: (Boolean) -> Unit, onUtterance: (String, SpeechEvent) -> Unit) {
        initialized = onInitialized; event = onUtterance
    }
    override fun languageAvailability() = availability
    override fun voices() = available
    override fun selectVoice(voice: BulgarianVoice): Boolean {
        if (voice.id in rejected) return false
        selected = voice; return true
    }
    override fun speak(text: String, rate: Float, utteranceId: String): Boolean {
        if (throwSpeaking) throw IllegalStateException("fake engine failed")
        if (speakSuccess) spoken += Spoken(text, rate, utteranceId)
        return speakSuccess
    }
    override fun stop(): Boolean { stopCount++; return stopSuccess }
    override fun shutdown() { shutdownCount++ }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PronunciationTest {
    private val target = PronunciationTarget("question-1", "Добър ден!")
    private fun TestScope.ready(fake: FakeTtsAdapter = FakeTtsAdapter()): Pair<BulgarianPronunciation, FakeTtsAdapter> {
        val speech = BulgarianPronunciation(this, { fake })
        speech.initialize(); speech.register(target); fake.initialized!!(true)
        return speech to fake
    }
    @Test fun initializationIsAsynchronousAndEarlyTapsNeverBecomeQueuedPlayback() = runTest {
        val fake=FakeTtsAdapter(); val speech=BulgarianPronunciation(this,{fake})
        speech.initialize(); speech.register(target)
        assertEquals(PronunciationStatus.INITIALIZING,speech.state.value.status)
        repeat(5) { assertFalse(speech.speak(target.owner)) }
        assertTrue(fake.spoken.isEmpty())
        fake.initialized!!(true)
        assertEquals(PronunciationStatus.READY,speech.state.value.status)
        assertTrue(fake.spoken.isEmpty()); assertTrue(speech.speak(target.owner)); speech.close()
    }
    @Test fun failedInitializationIsExplicitAndLateSuccessCannotReviveTheFailedEngine() = runTest {
        val fake=FakeTtsAdapter(); val speech=BulgarianPronunciation(this,{fake})
        speech.initialize(); speech.register(target); fake.initialized!!(false); fake.initialized!!(true)
        assertEquals(PronunciationStatus.ERROR,speech.state.value.status)
        assertEquals(1,fake.shutdownCount); assertFalse(speech.speak(target.owner)); speech.close()
    }
    @Test fun timedOutInitializationReleasesEngineAndIgnoresLateCallbacks() = runTest {
        val fake=FakeTtsAdapter();val speech=BulgarianPronunciation(this,{fake},100)
        speech.initialize();runCurrent();advanceTimeBy(101);runCurrent()
        assertEquals(PronunciationStatus.ERROR,speech.state.value.status);assertEquals(1,fake.shutdownCount)
        fake.initialized!!(true);assertEquals(PronunciationStatus.ERROR,speech.state.value.status);speech.close()
    }
    @Test fun retryUsesNewEngineAndIgnoresThePreviousInitializationCallback() = runTest {
        val old=FakeTtsAdapter();val next=FakeTtsAdapter();var calls=0
        val speech=BulgarianPronunciation(this,{if(calls++==0)old else next})
        speech.initialize();speech.initialize();old.initialized!!(true)
        assertEquals(PronunciationStatus.INITIALIZING,speech.state.value.status);assertEquals(1,old.shutdownCount)
        next.initialized!!(true);assertEquals(PronunciationStatus.READY,speech.state.value.status);speech.close()
    }
    @Test fun choosesOnlyInstalledLocalBulgarianEvenIfOtherVoicesHaveHigherQuality() = runTest {
        val fake=FakeTtsAdapter();fake.available=listOf(
            BulgarianVoice("ru","ru-RU",false,quality=1000),BulgarianVoice("cloud","bg-BG",true,quality=1000),
            BulgarianVoice("download","bg-BG",false,installed=false,quality=1000),
            BulgarianVoice("generic","BG",false,quality=100),BulgarianVoice("local","bg-BG",false,quality=300))
        val (speech,_)=ready(fake);assertEquals("local",fake.selected!!.id)
        assertTrue(speech.speak(target.owner));assertEquals("Добър ден!",fake.spoken.single().text);speech.close()
    }
    @Test fun languageSupportDoesNotImplyDownloadedVoiceData() = runTest {
        val fake=FakeTtsAdapter();fake.available=emptyList();val(speech,_)=ready(fake)
        assertEquals(PronunciationStatus.MISSING_DATA,speech.state.value.status)
        assertFalse(speech.speak(target.owner));assertTrue(fake.spoken.isEmpty());speech.close()
    }
    @Test fun explicitMissingLanguageDataNeverFallsBackToAnAdvertisedVoice() = runTest {
        val fake=FakeTtsAdapter();fake.availability=LanguageAvailability.MISSING_DATA;val(speech,_)=ready(fake)
        assertEquals(PronunciationStatus.MISSING_DATA,speech.state.value.status);assertNull(fake.selected)
        assertFalse(speech.speak(target.owner));speech.close()
    }
    @Test fun unsupportedBulgarianNeverSelectsRussianOrEnglish() = runTest {
        val fake=FakeTtsAdapter();fake.availability=LanguageAvailability.UNSUPPORTED
        fake.available=listOf(BulgarianVoice("ru","ru-RU",false),BulgarianVoice("en","en-US",false))
        val(speech,_)=ready(fake);assertEquals(PronunciationStatus.UNSUPPORTED,speech.state.value.status)
        assertFalse(speech.speak(target.owner));assertNull(fake.selected);speech.close()
    }
    @Test fun networkOnlyBulgarianIsReportedAndNeverUsed() = runTest {
        val fake=FakeTtsAdapter();fake.available=listOf(BulgarianVoice("cloud","bg-BG",true))
        val(speech,_)=ready(fake);assertEquals(PronunciationStatus.NETWORK_ONLY,speech.state.value.status)
        assertFalse(speech.speak(target.owner));assertTrue(fake.spoken.isEmpty());speech.close()
    }
    @Test fun notInstalledLocalVoiceIsNotTreatedAsOfflineReady() = runTest {
        val fake=FakeTtsAdapter();fake.available=listOf(BulgarianVoice("download","bg-BG",false,installed=false))
        val(speech,_)=ready(fake);assertEquals(PronunciationStatus.MISSING_DATA,speech.state.value.status)
        assertFalse(speech.speak(target.owner));speech.close()
    }
    @Test fun failedSelectionMayTryAnotherLocalBulgarianButNeverAnotherLanguage() = runTest {
        val fake=FakeTtsAdapter();fake.available=listOf(BulgarianVoice("first","bg-BG",false,quality=500),BulgarianVoice("second","bg",false))
        fake.rejected=setOf("first");val(speech,_)=ready(fake);assertEquals("second",fake.selected!!.id);speech.close()
    }
    @Test fun allRejectedLocalVoicesReportErrorWithoutSpeaking() = runTest {
        val fake=FakeTtsAdapter();fake.rejected=setOf("local-bg");val(speech,_)=ready(fake)
        assertEquals(PronunciationStatus.ERROR,speech.state.value.status);assertFalse(speech.speak(target.owner));speech.close()
    }
    @Test fun repeatedNormalAndSlowTapsCancelPreviousPlaybackAndIgnoreStaleEvents() = runTest {
        val(speech,fake)=ready();assertTrue(speech.speak(target.owner));val old=fake.spoken.last().id
        assertTrue(speech.speak(target.owner));assertTrue(speech.speak(target.owner,slow=true))
        assertEquals(listOf(1f,1f,0.5f),fake.spoken.map{it.rate});assertEquals(3,fake.stopCount)
        fake.event!!(old,SpeechEvent.ERROR);fake.event!!(old,SpeechEvent.DONE)
        assertEquals(PronunciationStatus.SPEAKING,speech.state.value.status)
        fake.event!!(fake.spoken.last().id,SpeechEvent.DONE)
        assertEquals(PronunciationStatus.READY,speech.state.value.status);assertNull(speech.state.value.owner);speech.close()
    }
    @Test fun failedCancellationNeverQueuesAnotherUtterance() = runTest {
        val(speech,fake)=ready();speech.speak(target.owner);fake.stopSuccess=false
        assertFalse(speech.speak(target.owner));assertEquals(1,fake.spoken.size)
        assertEquals(PronunciationStatus.ERROR,speech.state.value.status);speech.close()
    }
    @Test fun enginePlaybackErrorsPermitExplicitReplayAndNeverAutoplay() = runTest {
        val(speech,fake)=ready();speech.speak(target.owner);fake.event!!(fake.spoken.last().id,SpeechEvent.ERROR)
        assertEquals(PronunciationStatus.ERROR,speech.state.value.status);assertEquals(1,fake.spoken.size)
        assertTrue(speech.speak(target.owner));assertEquals(2,fake.spoken.size);speech.close()
    }
    @Test fun failedSpeakReturnAndExceptionBothRemainRecoverable() = runTest {
        val(speech,fake)=ready();fake.speakSuccess=false;assertFalse(speech.speak(target.owner))
        fake.throwSpeaking=true;assertFalse(speech.speak(target.owner));assertTrue(fake.spoken.isEmpty())
        fake.throwSpeaking=false;fake.speakSuccess=true;assertTrue(speech.speak(target.owner));speech.close()
    }
    @Test fun questionOrPageDisposalStopsItsPlaybackAndUnregisteredTextCannotBeSpoken() = runTest {
        val(speech,fake)=ready();speech.speak(target.owner);speech.unregister(target.owner)
        assertEquals(2,fake.stopCount);assertEquals(PronunciationStatus.READY,speech.state.value.status)
        assertFalse(speech.speak(target.owner));speech.close()
    }
    @Test fun disposingOldControlCannotStopAnotherControlsPlayback() = runTest {
        val(speech,fake)=ready();val other=PronunciationTarget("question-2","Къде си?")
        speech.speak(target.owner);speech.register(other);speech.speak(other.owner);speech.unregister(target.owner)
        assertEquals(2,fake.stopCount);assertEquals(other.owner,speech.state.value.owner);speech.close()
    }
    @Test fun changingRegisteredTextOrBackgroundStopInvalidatesOldCallbacks() = runTest {
        val(speech,fake)=ready();speech.speak(target.owner);val old=fake.spoken.last().id
        speech.register(target.copy(text="Котка"));assertEquals(PronunciationStatus.READY,speech.state.value.status)
        speech.speak(target.owner);speech.stop();fake.event!!(old,SpeechEvent.STARTED)
        assertEquals(PronunciationStatus.READY,speech.state.value.status);assertEquals("Котка",fake.spoken.last().text);speech.close()
    }
    @Test fun disappearedOrNowNetworkRequiredVoiceIsRecheckedBeforePlayback() = runTest {
        val(speech,fake)=ready();fake.available=listOf(BulgarianVoice("local-bg","bg-BG",true))
        assertFalse(speech.speak(target.owner));assertTrue(fake.spoken.isEmpty())
        assertEquals(PronunciationStatus.NETWORK_ONLY,speech.state.value.status);speech.close()
    }
    @Test fun closeIsIdempotentAndLateCallbacksCannotRestartSpeech() = runTest {
        val(speech,fake)=ready();speech.speak(target.owner);val id=fake.spoken.last().id
        speech.close();speech.close();fake.event!!(id,SpeechEvent.ERROR);fake.initialized!!(true)
        assertEquals(1,fake.shutdownCount);assertEquals(PronunciationStatus.CLOSED,speech.state.value.status)
        assertFalse(speech.speak(target.owner));speech.initialize();assertEquals(1,fake.shutdownCount)
    }
    @Test fun emptyOrUnknownTargetsNeverReachEngine() = runTest {
        val(speech,fake)=ready();speech.register(PronunciationTarget("empty"," \n "))
        assertFalse(speech.speak("empty"));assertFalse(speech.speak("not-registered"));assertTrue(fake.spoken.isEmpty());speech.close()
    }
    @Test fun constructorFailureIsExplicitWithoutPreventingLaterRetry() = runTest {
        val fake=FakeTtsAdapter();var first=true;val speech=BulgarianPronunciation(this,{
            if(first){first=false;throw IllegalStateException("no engine")};fake })
        speech.initialize();assertEquals(PronunciationStatus.ERROR,speech.state.value.status)
        speech.initialize();fake.initialized!!(true);assertEquals(PronunciationStatus.READY,speech.state.value.status);speech.close()
    }
}

class PronunciationPolicyTest {
    @Test fun bulgarianFlashcardSourceIsImmediatelyAvailableButTargetRequiresReveal() {
        val bg=FlashcardQuestion(1,1,CardDirection.BG_TO_RU,listOf("котка"),false,listOf("кошка"))
        assertEquals("котка",PronunciationPolicy.flashcard(8,bg)!!.text)
        val ru=bg.copy(direction=CardDirection.RU_TO_BG,prompt=listOf("кошка"),answer=listOf("котка"))
        assertNull(PronunciationPolicy.flashcard(8,ru))
        assertEquals("котка",PronunciationPolicy.flashcard(8,ru.copy(revealed=true))!!.text)
    }
    @Test fun typingSourceIsImmediatelyAvailableAndFeedbackNeverMakesRussianSpoken() {
        val q=TypingQuestion(1,1,TypingDirection.BG_TO_RU,listOf("добър ден"),null)
        assertEquals("добър ден",PronunciationPolicy.typing(2,q)!!.text)
        assertEquals("добър ден",PronunciationPolicy.typing(2,q.copy(feedback=TypingFeedback(false,"ошибка",listOf("добрый день"))))!!.text)
    }
    @Test fun hiddenTypingTargetIsUnavailableUntilSubmissionAndUsesApprovedAnswerNotMistake() {
        val q=TypingQuestion(1,1,TypingDirection.RU_TO_BG,listOf("дом"),null)
        assertNull(PronunciationPolicy.typing(2,q))
        assertEquals("къща",PronunciationPolicy.typing(2,q.copy(feedback=TypingFeedback(false,"кушта",listOf("къща"))))!!.text)
    }
    @Test fun sentencePrimaryAndApprovedAlternativesAreAvailableAfterSubmissionIncludingPendingSelfRating() {
        val q=SentenceQuestion(1,1,"Я здесь.",null)
        assertTrue(PronunciationPolicy.sentence(3,q).isEmpty())
        val feedback=SentenceFeedback("Аз тука",false,null,listOf("Тук съм.","Аз съм тук."),null)
        assertTrue(feedback.needsSelfAssessment)
        val targets=PronunciationPolicy.sentence(3,q.copy(feedback=feedback))
        assertEquals(listOf("Тук съм.","Аз съм тук."),targets.map{it.text});assertEquals(2,targets.map{it.owner}.distinct().size)
        assertFalse(targets.any{it.text==feedback.submitted})
    }
    @Test fun ownerIdentitiesIncludeSessionAndQuestionToPreventCrossSessionCollisions() {
        val q=TypingQuestion(1,1,TypingDirection.BG_TO_RU,listOf("котка"),null)
        assertNotEquals(PronunciationPolicy.typing(1,q)!!.owner,PronunciationPolicy.typing(2,q)!!.owner)
        assertNotEquals(PronunciationPolicy.typing(1,q)!!.owner,PronunciationPolicy.typing(1,q.copy(id=2))!!.owner)
    }
    @Test fun vocabularyIsImmediatelyAvailableAndEmptyReferencesAreExcluded() {
        assertEquals("добър ден",PronunciationPolicy.vocabulary(1," добър ден ")!!.text)
        assertNull(PronunciationPolicy.vocabulary(1," \n "))
        val q=SentenceQuestion(1,1,"Источник",SentenceFeedback("ответ",false,null,listOf(" ","Тук съм."),null))
        assertEquals(listOf("Тук съм."),PronunciationPolicy.sentence(2,q).map{it.text})
    }
}
