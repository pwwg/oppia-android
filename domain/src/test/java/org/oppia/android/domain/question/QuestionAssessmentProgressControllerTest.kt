package org.oppia.android.domain.question

import android.app.Application
import android.content.Context
import androidx.lifecycle.Observer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import dagger.Provides
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.oppia.android.app.model.AnsweredQuestionOutcome
import org.oppia.android.app.model.EphemeralQuestion
import org.oppia.android.app.model.EphemeralState.StateTypeCase.COMPLETED_STATE
import org.oppia.android.app.model.EphemeralState.StateTypeCase.PENDING_STATE
import org.oppia.android.app.model.EphemeralState.StateTypeCase.TERMINAL_STATE
import org.oppia.android.app.model.FractionGrade
import org.oppia.android.app.model.Hint
import org.oppia.android.app.model.InteractionObject
import org.oppia.android.app.model.Solution
import org.oppia.android.app.model.UserAnswer
import org.oppia.android.app.model.UserAssessmentPerformance
import org.oppia.android.domain.classify.InteractionsModule
import org.oppia.android.domain.classify.rules.continueinteraction.ContinueModule
import org.oppia.android.domain.classify.rules.dragAndDropSortInput.DragDropSortInputModule
import org.oppia.android.domain.classify.rules.fractioninput.FractionInputModule
import org.oppia.android.domain.classify.rules.imageClickInput.ImageClickInputModule
import org.oppia.android.domain.classify.rules.itemselectioninput.ItemSelectionInputModule
import org.oppia.android.domain.classify.rules.multiplechoiceinput.MultipleChoiceInputModule
import org.oppia.android.domain.classify.rules.numberwithunits.NumberWithUnitsRuleModule
import org.oppia.android.domain.classify.rules.numericinput.NumericInputRuleModule
import org.oppia.android.domain.classify.rules.ratioinput.RatioInputModule
import org.oppia.android.domain.classify.rules.textinput.TextInputRuleModule
import org.oppia.android.domain.oppialogger.LogStorageModule
import org.oppia.android.domain.topic.TEST_SKILL_ID_0
import org.oppia.android.domain.topic.TEST_SKILL_ID_1
import org.oppia.android.domain.topic.TEST_SKILL_ID_2
import org.oppia.android.testing.FakeExceptionLogger
import org.oppia.android.testing.TestLogReportingModule
import org.oppia.android.testing.robolectric.RobolectricModule
import org.oppia.android.testing.threading.TestCoroutineDispatchers
import org.oppia.android.testing.threading.TestDispatcherModule
import org.oppia.android.testing.time.FakeOppiaClockModule
import org.oppia.android.util.caching.testing.CachingTestModule
import org.oppia.android.util.data.AsyncResult
import org.oppia.android.util.data.DataProviders.Companion.toLiveData
import org.oppia.android.util.data.DataProvidersInjector
import org.oppia.android.util.data.DataProvidersInjectorProvider
import org.oppia.android.util.logging.EnableConsoleLog
import org.oppia.android.util.logging.EnableFileLog
import org.oppia.android.util.logging.GlobalLogLevel
import org.oppia.android.util.logging.LogLevel
import org.oppia.android.util.networking.NetworkConnectionUtilDebugModule
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import javax.inject.Inject
import javax.inject.Singleton

/** Tests for [QuestionAssessmentProgressController]. */
@RunWith(AndroidJUnit4::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(application = QuestionAssessmentProgressControllerTest.TestApplication::class)
class QuestionAssessmentProgressControllerTest {
  private val TEST_SKILL_ID_LIST_012 =
    listOf(TEST_SKILL_ID_0, TEST_SKILL_ID_1, TEST_SKILL_ID_2) // questions 0, 1, 2, 3, 4, 5
  private val TEST_SKILL_ID_LIST_02 =
    listOf(TEST_SKILL_ID_0, TEST_SKILL_ID_2) // questions 0, 1, 2, 4, 5
  private val TEST_SKILL_ID_LIST_01 =
    listOf(TEST_SKILL_ID_0, TEST_SKILL_ID_1) // questions 0, 1, 2, 3
  private val TEST_SKILL_ID_LIST_2 = listOf(TEST_SKILL_ID_2) // questions 2, 4, 5

  @Rule
  @JvmField
  val mockitoRule: MockitoRule = MockitoJUnit.rule()

  @Inject
  lateinit var questionTrainingController: QuestionTrainingController

  @Inject
  lateinit var questionAssessmentProgressController: QuestionAssessmentProgressController

  @Inject
  lateinit var fakeExceptionLogger: FakeExceptionLogger

  @Inject
  lateinit var testCoroutineDispatchers: TestCoroutineDispatchers

  @Mock
  lateinit var mockCurrentQuestionLiveDataObserver: Observer<AsyncResult<EphemeralQuestion>>

  @Mock
  lateinit var mockScoreAndMasteryLiveDataObserver:
    Observer<AsyncResult<UserAssessmentPerformance>>

  @Mock
  lateinit var mockAsyncResultLiveDataObserver: Observer<AsyncResult<Any>>

  @Mock
  lateinit var mockAsyncNullableResultLiveDataObserver: Observer<AsyncResult<Any?>>

  @Mock
  lateinit var mockAsyncAnswerOutcomeObserver: Observer<AsyncResult<AnsweredQuestionOutcome>>

  @Mock
  lateinit var mockAsyncHintObserver: Observer<AsyncResult<Hint>>

  @Mock
  lateinit var mockAsyncSolutionObserver: Observer<AsyncResult<Solution>>

  @Captor
  lateinit var currentQuestionResultCaptor: ArgumentCaptor<AsyncResult<EphemeralQuestion>>

  @Captor
  lateinit var asyncResultCaptor: ArgumentCaptor<AsyncResult<Any>>

  @Captor
  lateinit var performanceCalculationCaptor: ArgumentCaptor<AsyncResult<UserAssessmentPerformance>>

  @Captor
  lateinit var asyncNullableResultCaptor: ArgumentCaptor<AsyncResult<Any?>>

  @Captor
  lateinit var asyncAnswerOutcomeCaptor: ArgumentCaptor<AsyncResult<AnsweredQuestionOutcome>>

  @Test
  fun testGetCurrentQuestion_noSessionStarted_returnsPendingResult() {
    setUpTestApplicationWithSeed(questionSeed = 0)

    val resultLiveData =
      questionAssessmentProgressController.getCurrentQuestion().toLiveData()
    resultLiveData.observeForever(mockCurrentQuestionLiveDataObserver)
    testCoroutineDispatchers.runCurrent()

    verify(mockCurrentQuestionLiveDataObserver).onChanged(currentQuestionResultCaptor.capture())
    assertThat(currentQuestionResultCaptor.value.isPending()).isTrue()
  }

  @Test
  fun testGetCurrentQuestion_sessionStarted_withEmptyQuestionList_fails() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    questionTrainingController.startQuestionTrainingSession(listOf())

    val resultLiveData =
      questionAssessmentProgressController.getCurrentQuestion().toLiveData()
    resultLiveData.observeForever(mockCurrentQuestionLiveDataObserver)
    testCoroutineDispatchers.runCurrent()

    verify(mockCurrentQuestionLiveDataObserver, atLeastOnce()).onChanged(
      currentQuestionResultCaptor.capture()
    )
    assertThat(currentQuestionResultCaptor.value.isFailure()).isTrue()
    assertThat(currentQuestionResultCaptor.value.getErrorOrNull())
      .hasCauseThat()
      .hasMessageThat()
      .contains("Expected at least 1 question")
  }

  @Test
  fun testStartTrainingSession_succeeds() {
    setUpTestApplicationWithSeed(questionSeed = 0)

    val resultLiveData =
      questionTrainingController.startQuestionTrainingSession(TEST_SKILL_ID_LIST_012)
    resultLiveData.observeForever(mockAsyncResultLiveDataObserver)
    testCoroutineDispatchers.runCurrent()

    verify(mockAsyncResultLiveDataObserver).onChanged(asyncResultCaptor.capture())
    assertThat(asyncResultCaptor.value.isSuccess()).isTrue()
  }

  @Test
  fun testGetCurrentQuestion_playSession_returnsPendingResultFromLoadingSession() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    val currentQuestionLiveData =
      questionAssessmentProgressController.getCurrentQuestion().toLiveData()
    currentQuestionLiveData.observeForever(mockCurrentQuestionLiveDataObserver)
    testCoroutineDispatchers.runCurrent()

    startTrainingSession(TEST_SKILL_ID_LIST_012)

    // The second-to-latest result stays pending since the session was loading (the actual result is
    // the fully loaded session). This is only true if the observer begins before starting to load
    // the session.
    verify(mockCurrentQuestionLiveDataObserver, Mockito.atLeast(2)).onChanged(
      currentQuestionResultCaptor.capture()
    )
    val results = currentQuestionResultCaptor.allValues
    assertThat(results[results.size - 2].isPending()).isTrue()
  }

  @Test
  fun testGetCurrentQuestion_playSession_loaded_returnsInitialQuestionPending() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    startTrainingSession(TEST_SKILL_ID_LIST_012)

    val currentQuestionLiveData =
      questionAssessmentProgressController.getCurrentQuestion().toLiveData()
    currentQuestionLiveData.observeForever(mockCurrentQuestionLiveDataObserver)
    testCoroutineDispatchers.runCurrent()

    verify(
      mockCurrentQuestionLiveDataObserver,
      atLeastOnce()
    ).onChanged(currentQuestionResultCaptor.capture())
    assertThat(currentQuestionResultCaptor.value.isSuccess()).isTrue()
    val ephemeralQuestion = currentQuestionResultCaptor.value.getOrThrow()
    assertThat(ephemeralQuestion.currentQuestionIndex).isEqualTo(0)
    assertThat(ephemeralQuestion.totalQuestionCount).isGreaterThan(0)
    assertThat(ephemeralQuestion.ephemeralState.stateTypeCase).isEqualTo(PENDING_STATE)
    assertThat(ephemeralQuestion.ephemeralState.state.content.html)
      .contains("What fraction does 'quarter'")
  }

  @Test
  fun testGetCurrentQuestion_playInvalidSession_thenPlayValidExp_returnsInitialPendingQuestion() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    // Start with starting an invalid training session.
    startTrainingSession(listOf())
    endTrainingSession()

    // Then a valid one.
    startTrainingSession(TEST_SKILL_ID_LIST_012)
    val currentQuestionLiveData =
      questionAssessmentProgressController.getCurrentQuestion().toLiveData()
    currentQuestionLiveData.observeForever(mockCurrentQuestionLiveDataObserver)
    testCoroutineDispatchers.runCurrent()

    // The latest result should correspond to the valid ID, and the progress controller should
    // gracefully recover.
    verify(mockCurrentQuestionLiveDataObserver, atLeastOnce()).onChanged(
      currentQuestionResultCaptor.capture()
    )
    assertThat(currentQuestionResultCaptor.value.isSuccess()).isTrue()
    val ephemeralQuestion = currentQuestionResultCaptor.value.getOrThrow()
    assertThat(ephemeralQuestion.currentQuestionIndex).isEqualTo(0)
    assertThat(ephemeralQuestion.totalQuestionCount).isGreaterThan(0)
    assertThat(ephemeralQuestion.ephemeralState.stateTypeCase).isEqualTo(PENDING_STATE)
    assertThat(ephemeralQuestion.ephemeralState.state.content.html)
      .contains("What fraction does 'quarter'")
  }

  @Test
  fun testStopTrainingSession_withoutStartingSession_fails() {
    setUpTestApplicationWithSeed(questionSeed = 0)

    val resultLiveData =
      questionTrainingController.stopQuestionTrainingSession()
    testCoroutineDispatchers.runCurrent()

    assertThat(resultLiveData.value).isNotNull()
    assertThat(resultLiveData.value!!.isFailure()).isTrue()
    assertThat(resultLiveData.value!!.getErrorOrNull())
      .hasMessageThat()
      .contains("Cannot stop a new training session which wasn't started")
  }

  @Test
  fun testStartTrainingSession_withoutFinishingPrevious_fails() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    questionTrainingController.startQuestionTrainingSession(TEST_SKILL_ID_LIST_012)

    val resultLiveData =
      questionTrainingController.startQuestionTrainingSession(TEST_SKILL_ID_LIST_02)
    testCoroutineDispatchers.runCurrent()

    assertThat(resultLiveData.value).isNotNull()
    assertThat(resultLiveData.value!!.isFailure()).isTrue()
    assertThat(resultLiveData.value!!.getErrorOrNull())
      .hasMessageThat()
      .contains("Cannot start a new training session until the previous one is completed")
  }

  @Test
  fun testStopTrainingSession_afterStartingPreviousSession_succeeds() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    questionTrainingController.startQuestionTrainingSession(TEST_SKILL_ID_LIST_012)

    val resultLiveData =
      questionTrainingController.stopQuestionTrainingSession()
    testCoroutineDispatchers.runCurrent()

    assertThat(resultLiveData.value).isNotNull()
    assertThat(resultLiveData.value!!.isSuccess()).isTrue()
  }

  @Test
  fun testGetCurrentQuestion_playSecondSession_afterFinishingPrev_loaded_returnsInitialQuestion() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    val currentQuestionLiveData =
      questionAssessmentProgressController.getCurrentQuestion().toLiveData()
    currentQuestionLiveData.observeForever(mockCurrentQuestionLiveDataObserver)
    // Start with playing a valid session, then stop.
    startTrainingSession(TEST_SKILL_ID_LIST_012)
    endTrainingSession()

    // Then another valid one.
    startTrainingSession(TEST_SKILL_ID_LIST_2)

    // The latest result should correspond to the valid ID, and the progress controller should
    // gracefully recover.
    verify(mockCurrentQuestionLiveDataObserver, atLeastOnce()).onChanged(
      currentQuestionResultCaptor.capture()
    )
    assertThat(currentQuestionResultCaptor.value.isSuccess()).isTrue()
    val ephemeralQuestion = currentQuestionResultCaptor.value.getOrThrow()
    assertThat(ephemeralQuestion.currentQuestionIndex).isEqualTo(0)
    assertThat(ephemeralQuestion.totalQuestionCount).isGreaterThan(0)
    assertThat(ephemeralQuestion.ephemeralState.stateTypeCase).isEqualTo(PENDING_STATE)
    assertThat(ephemeralQuestion.ephemeralState.state.content.html)
      .contains("of a cake, what does the 10 represent?")
  }

  @Test
  fun testSubmitAnswer_beforePlaying_failsWithError() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    val result =
      questionAssessmentProgressController.submitAnswer(createMultipleChoiceAnswer(0))
    result.observeForever(mockAsyncAnswerOutcomeObserver)
    testCoroutineDispatchers.runCurrent()

    // Verify that the answer submission failed.
    verify(
      mockAsyncAnswerOutcomeObserver,
      atLeastOnce()
    ).onChanged(asyncAnswerOutcomeCaptor.capture())
    assertThat(asyncAnswerOutcomeCaptor.value.isFailure()).isTrue()
    assertThat(asyncAnswerOutcomeCaptor.value.getErrorOrNull())
      .hasMessageThat()
      .contains("Cannot submit an answer if a training session has not yet begun.")
  }

  @Test
  fun testSubmitAnswer_forMultipleChoice_correctAnswer_succeeds() {
    setUpTestApplicationWithSeed(questionSeed = 6)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    startTrainingSession(TEST_SKILL_ID_LIST_2)

    val result =
      questionAssessmentProgressController.submitAnswer(createMultipleChoiceAnswer(1))
    result.observeForever(mockAsyncAnswerOutcomeObserver)
    testCoroutineDispatchers.runCurrent()

    // Verify that the answer submission was successful.
    verify(
      mockAsyncAnswerOutcomeObserver,
      atLeastOnce()
    ).onChanged(asyncAnswerOutcomeCaptor.capture())
    assertThat(asyncAnswerOutcomeCaptor.value.isSuccess()).isTrue()
  }

  @Test
  fun testSubmitAnswer_forMultipleChoice_correctAnswer_returnsOutcomeWithTransition() {
    setUpTestApplicationWithSeed(questionSeed = 6)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    startTrainingSession(TEST_SKILL_ID_LIST_2)

    val result =
      questionAssessmentProgressController.submitAnswer(createMultipleChoiceAnswer(1))
    result.observeForever(mockAsyncAnswerOutcomeObserver)
    testCoroutineDispatchers.runCurrent()

    // Verify that the answer submission was successful.
    verify(
      mockAsyncAnswerOutcomeObserver,
      atLeastOnce()
    ).onChanged(asyncAnswerOutcomeCaptor.capture())
    val answerOutcome = asyncAnswerOutcomeCaptor.value.getOrThrow()
    assertThat(answerOutcome.feedback.html).contains("That's correct!")
    assertThat(answerOutcome.isCorrectAnswer).isTrue()
  }

  @Test
  fun testSubmitAnswer_forMultipleChoice_wrongAnswer_succeeds() {
    setUpTestApplicationWithSeed(questionSeed = 6)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    startTrainingSession(TEST_SKILL_ID_LIST_2)

    val result =
      questionAssessmentProgressController.submitAnswer(createMultipleChoiceAnswer(0))
    result.observeForever(mockAsyncAnswerOutcomeObserver)
    testCoroutineDispatchers.runCurrent()

    // Verify that the answer submission was successful.
    verify(
      mockAsyncAnswerOutcomeObserver,
      atLeastOnce()
    ).onChanged(asyncAnswerOutcomeCaptor.capture())
    assertThat(asyncAnswerOutcomeCaptor.value.isSuccess()).isTrue()
  }

  @Test
  fun testSubmitAnswer_forMultChoice_wrongAnswer_providesDefaultFeedbackAndNewQuestionTransition() {
    setUpTestApplicationWithSeed(questionSeed = 6)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    startTrainingSession(TEST_SKILL_ID_LIST_2)

    val result =
      questionAssessmentProgressController.submitAnswer(createMultipleChoiceAnswer(0))
    result.observeForever(mockAsyncAnswerOutcomeObserver)
    testCoroutineDispatchers.runCurrent()

    // Verify that the answer submission was successful.
    verify(
      mockAsyncAnswerOutcomeObserver,
      atLeastOnce()
    ).onChanged(asyncAnswerOutcomeCaptor.capture())
    val answerOutcome = asyncAnswerOutcomeCaptor.value.getOrThrow()
    assertThat(answerOutcome.feedback.html).contains("Incorrect. Try again.")
    assertThat(answerOutcome.isCorrectAnswer).isFalse()
  }

  @Test
  fun testGetCurrentQuestion_afterSubmittingCorrectMultiChoiceAnswer_becomesCompletedQuestion() {
    setUpTestApplicationWithSeed(questionSeed = 6)
    val currentQuestionLiveData =
      questionAssessmentProgressController.getCurrentQuestion().toLiveData()
    currentQuestionLiveData.observeForever(mockCurrentQuestionLiveDataObserver)
    startTrainingSession(TEST_SKILL_ID_LIST_2)

    submitMultipleChoiceAnswer(1)

    // Verify that the current state updates. It should stay pending, and the wrong answer should be
    // appended.
    verify(mockCurrentQuestionLiveDataObserver, atLeastOnce()).onChanged(
      currentQuestionResultCaptor.capture()
    )
    assertThat(currentQuestionResultCaptor.value.isSuccess()).isTrue()
    val ephemeralQuestion = currentQuestionResultCaptor.value.getOrThrow()
    assertThat(ephemeralQuestion.currentQuestionIndex).isEqualTo(0)
    assertThat(ephemeralQuestion.totalQuestionCount).isEqualTo(3)
    assertThat(ephemeralQuestion.ephemeralState.stateTypeCase).isEqualTo(COMPLETED_STATE)
    val completedState = ephemeralQuestion.ephemeralState.completedState
    assertThat(completedState.getAnswer(0).userAnswer.answer.nonNegativeInt).isEqualTo(1)
    assertThat(completedState.getAnswer(0).feedback.html).contains("That's correct!")
  }

  @Test
  fun testGetCurrentQuestion_afterSubmittingWrongMultiChoiceAnswer_updatesPendingQuestion() {
    setUpTestApplicationWithSeed(questionSeed = 6)
    val currentQuestionLiveData =
      questionAssessmentProgressController.getCurrentQuestion().toLiveData()
    currentQuestionLiveData.observeForever(mockCurrentQuestionLiveDataObserver)
    startTrainingSession(TEST_SKILL_ID_LIST_2)

    submitMultipleChoiceAnswer(0)

    // Verify that the current state updates. It should stay pending, and the wrong answer should be
    // appended.
    verify(mockCurrentQuestionLiveDataObserver, atLeastOnce()).onChanged(
      currentQuestionResultCaptor.capture()
    )
    assertThat(currentQuestionResultCaptor.value.isSuccess()).isTrue()
    val ephemeralQuestion = currentQuestionResultCaptor.value.getOrThrow()
    assertThat(ephemeralQuestion.currentQuestionIndex).isEqualTo(0)
    assertThat(ephemeralQuestion.totalQuestionCount).isEqualTo(3)
    assertThat(ephemeralQuestion.ephemeralState.stateTypeCase).isEqualTo(PENDING_STATE)
    val pendingState = ephemeralQuestion.ephemeralState.pendingState
    assertThat(pendingState.getWrongAnswer(0).userAnswer.answer.nonNegativeInt).isEqualTo(0)
    assertThat(pendingState.getWrongAnswer(0).feedback.html).contains("Incorrect. Try again.")
  }

  @Test
  fun testGetCurrentQuestion_afterSubmitWrongThenRightAnswer_updatesToQuestionWithBothAnswers() {
    setUpTestApplicationWithSeed(questionSeed = 6)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    startTrainingSession(TEST_SKILL_ID_LIST_2)
    submitMultipleChoiceAnswer(0)

    submitMultipleChoiceAnswer(1)

    // Verify that the current state updates. It should now be completed with both the wrong and
    // correct answers.
    verify(mockCurrentQuestionLiveDataObserver, atLeastOnce()).onChanged(
      currentQuestionResultCaptor.capture()
    )
    assertThat(currentQuestionResultCaptor.value.isSuccess()).isTrue()
    val ephemeralQuestion = currentQuestionResultCaptor.value.getOrThrow()
    assertThat(ephemeralQuestion.currentQuestionIndex).isEqualTo(0)
    assertThat(ephemeralQuestion.totalQuestionCount).isEqualTo(3)
    assertThat(ephemeralQuestion.ephemeralState.stateTypeCase).isEqualTo(COMPLETED_STATE)
    val completedState = ephemeralQuestion.ephemeralState.completedState
    assertThat(completedState.getAnswer(0).userAnswer.answer.nonNegativeInt).isEqualTo(0)
    assertThat(completedState.getAnswer(0).feedback.html).contains("Incorrect. Try again.")
    assertThat(completedState.getAnswer(1).userAnswer.answer.nonNegativeInt).isEqualTo(1)
    assertThat(completedState.getAnswer(1).feedback.html).contains("That's correct!")
  }

  @Test
  fun testMoveToNext_beforePlaying_failsWithError() {
    setUpTestApplicationWithSeed(questionSeed = 0)

    val moveToStateResult =
      questionAssessmentProgressController.moveToNextQuestion()
    moveToStateResult.observeForever(mockAsyncNullableResultLiveDataObserver)

    verify(mockAsyncNullableResultLiveDataObserver, atLeastOnce()).onChanged(
      asyncNullableResultCaptor.capture()
    )
    assertThat(asyncNullableResultCaptor.value.isFailure()).isTrue()
    assertThat(asyncNullableResultCaptor.value.getErrorOrNull())
      .hasMessageThat()
      .contains("Cannot navigate to a next question if a training session has not begun.")
  }

  @Test
  fun testMoveToNext_forPendingInitialQuestion_failsWithError() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    startTrainingSession(TEST_SKILL_ID_LIST_2)

    val moveToStateResult =
      questionAssessmentProgressController.moveToNextQuestion()
    moveToStateResult.observeForever(mockAsyncNullableResultLiveDataObserver)
    testCoroutineDispatchers.runCurrent()

    // Verify that we can't move ahead since the current state isn't yet completed.
    verify(mockAsyncNullableResultLiveDataObserver, atLeastOnce()).onChanged(
      asyncNullableResultCaptor.capture()
    )
    assertThat(asyncNullableResultCaptor.value.isFailure()).isTrue()
    assertThat(asyncNullableResultCaptor.value.getErrorOrNull())
      .hasMessageThat()
      .contains("Cannot navigate to next state; at most recent state.")
  }

  @Test
  fun testMoveToNext_forCompletedQuestion_succeeds() {
    setUpTestApplicationWithSeed(questionSeed = 6)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    startTrainingSession(TEST_SKILL_ID_LIST_2)
    submitMultipleChoiceAnswer(1)

    val moveToStateResult =
      questionAssessmentProgressController.moveToNextQuestion()
    moveToStateResult.observeForever(mockAsyncNullableResultLiveDataObserver)
    testCoroutineDispatchers.runCurrent()

    verify(mockAsyncNullableResultLiveDataObserver, atLeastOnce()).onChanged(
      asyncNullableResultCaptor.capture()
    )
    assertThat(asyncNullableResultCaptor.value.isSuccess()).isTrue()
  }

  @Test
  fun testMoveToNext_forCompletedQuestion_movesToNextQuestion() {
    setUpTestApplicationWithSeed(questionSeed = 6)
    val currentQuestionLiveData =
      questionAssessmentProgressController.getCurrentQuestion().toLiveData()
    currentQuestionLiveData.observeForever(mockCurrentQuestionLiveDataObserver)
    startTrainingSession(TEST_SKILL_ID_LIST_2)
    submitMultipleChoiceAnswer(1)

    moveToNextQuestion()

    verify(mockCurrentQuestionLiveDataObserver, atLeastOnce()).onChanged(
      currentQuestionResultCaptor.capture()
    )
    assertThat(currentQuestionResultCaptor.value.isSuccess()).isTrue()
    val currentQuestion = currentQuestionResultCaptor.value.getOrThrow()
    assertThat(currentQuestion.ephemeralState.state.content.html).contains("1/2 + 1/4")
  }

  @Test
  fun testMoveToNext_afterMovingFromCompletedQuestion_failsWithError() {
    setUpTestApplicationWithSeed(questionSeed = 6)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    startTrainingSession(TEST_SKILL_ID_LIST_2)
    submitMultipleChoiceAnswer(1)
    moveToNextQuestion()

    // Try skipping past the current state.
    val moveToStateResult =
      questionAssessmentProgressController.moveToNextQuestion()
    moveToStateResult.observeForever(mockAsyncNullableResultLiveDataObserver)
    testCoroutineDispatchers.runCurrent()

    // Verify we can't move ahead since the new state isn't yet completed.
    verify(mockAsyncNullableResultLiveDataObserver, atLeastOnce()).onChanged(
      asyncNullableResultCaptor.capture()
    )
    assertThat(asyncNullableResultCaptor.value.isFailure()).isTrue()
    assertThat(asyncNullableResultCaptor.value.getErrorOrNull())
      .hasMessageThat()
      .contains("Cannot navigate to next state; at most recent state.")
  }

  @Test
  fun testSubmitAnswer_forTextInput_correctAnswer_returnsOutcomeWithTransition() {
    setUpTestApplicationWithSeed(questionSeed = 2)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    startTrainingSession(TEST_SKILL_ID_LIST_01)

    val result =
      questionAssessmentProgressController.submitAnswer(createTextInputAnswer("1/4"))
    result.observeForever(mockAsyncAnswerOutcomeObserver)
    testCoroutineDispatchers.runCurrent()

    // Verify that the answer submission was successful.
    verify(
      mockAsyncAnswerOutcomeObserver,
      atLeastOnce()
    ).onChanged(asyncAnswerOutcomeCaptor.capture())
    val answerOutcome = asyncAnswerOutcomeCaptor.value.getOrThrow()
    assertThat(answerOutcome.isCorrectAnswer).isTrue()
    assertThat(answerOutcome.feedback.html).contains("That's correct!")
  }

  @Test
  fun testSubmitAnswer_forTextInput_wrongAnswer_returnsDefaultOutcome() {
    setUpTestApplicationWithSeed(questionSeed = 2)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    startTrainingSession(TEST_SKILL_ID_LIST_01)

    val result =
      questionAssessmentProgressController.submitAnswer(createTextInputAnswer("2/4"))
    result.observeForever(mockAsyncAnswerOutcomeObserver)
    testCoroutineDispatchers.runCurrent()

    // Verify that the answer was wrong, and that there's no handler for it so the default outcome
    // is returned.
    verify(
      mockAsyncAnswerOutcomeObserver,
      atLeastOnce()
    ).onChanged(asyncAnswerOutcomeCaptor.capture())
    val answerOutcome = asyncAnswerOutcomeCaptor.value.getOrThrow()
    assertThat(answerOutcome.isCorrectAnswer).isFalse()
    assertThat(answerOutcome.feedback.html).isEmpty()
  }

  @Test
  fun testGetCurrentQuestion_secondQuestion_submitRightAnswer_pendingQuestionBecomesCompleted() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    startTrainingSession(TEST_SKILL_ID_LIST_2)
    submitNumericInputAnswerAndMoveToNextQuestion(3.0)

    val result =
      questionAssessmentProgressController.submitAnswer(createNumericInputAnswer(5.0))
    result.observeForever(mockAsyncAnswerOutcomeObserver)
    testCoroutineDispatchers.runCurrent()

    // Verify that the current state updates. It should stay pending, and the wrong answer should be
    // appended.
    verify(mockCurrentQuestionLiveDataObserver, atLeastOnce()).onChanged(
      currentQuestionResultCaptor.capture()
    )
    assertThat(currentQuestionResultCaptor.value.isSuccess()).isTrue()
    val currentQuestion = currentQuestionResultCaptor.value.getOrThrow()
    assertThat(currentQuestion.currentQuestionIndex).isEqualTo(1)
    assertThat(currentQuestion.totalQuestionCount).isEqualTo(3)
    assertThat(currentQuestion.ephemeralState.stateTypeCase).isEqualTo(COMPLETED_STATE)
    val completedState = currentQuestion.ephemeralState.completedState
    assertThat(completedState.answerCount).isEqualTo(1)
    assertThat(completedState.getAnswer(0).userAnswer.answer.real)
      .isWithin(1e-5).of(5.0)
    assertThat(completedState.getAnswer(0).feedback.html).contains("That's correct!")
  }

  @Test
  fun testGetCurrentQuestion_secondQuestion_submitWrongAnswer_updatePendingQuestion() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    startTrainingSession(TEST_SKILL_ID_LIST_2)
    submitNumericInputAnswerAndMoveToNextQuestion(3.0)

    val result =
      questionAssessmentProgressController.submitAnswer(createNumericInputAnswer(4.0))
    result.observeForever(mockAsyncAnswerOutcomeObserver)
    testCoroutineDispatchers.runCurrent()

    // Verify that the current state updates. It should now be completed with the correct answer.
    verify(mockCurrentQuestionLiveDataObserver, atLeastOnce()).onChanged(
      currentQuestionResultCaptor.capture()
    )
    assertThat(currentQuestionResultCaptor.value.isSuccess()).isTrue()
    val currentQuestion = currentQuestionResultCaptor.value.getOrThrow()
    assertThat(currentQuestion.currentQuestionIndex).isEqualTo(1)
    assertThat(currentQuestion.totalQuestionCount).isEqualTo(3)
    assertThat(currentQuestion.ephemeralState.stateTypeCase).isEqualTo(PENDING_STATE)
    val pendingState = currentQuestion.ephemeralState.pendingState
    assertThat(pendingState.wrongAnswerCount).isEqualTo(1)
    assertThat(pendingState.getWrongAnswer(0).userAnswer.answer.real)
      .isWithin(1e-5).of(4.0)
    assertThat(pendingState.getWrongAnswer(0).feedback.html).isEmpty()
  }

  @Test
  fun testSubmitAnswer_forNumericInput_correctAnswer_returnsOutcomeWithTransition() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    startTrainingSession(TEST_SKILL_ID_LIST_2)

    val result =
      questionAssessmentProgressController.submitAnswer(createNumericInputAnswer(3.0))
    result.observeForever(mockAsyncAnswerOutcomeObserver)
    testCoroutineDispatchers.runCurrent()

    // Verify that the answer submission was successful.
    verify(
      mockAsyncAnswerOutcomeObserver,
      atLeastOnce()
    ).onChanged(asyncAnswerOutcomeCaptor.capture())
    val answerOutcome = asyncAnswerOutcomeCaptor.value.getOrThrow()
    assertThat(answerOutcome.isCorrectAnswer).isTrue()
    assertThat(answerOutcome.feedback.html).contains("That's correct!")
  }

  @Test
  fun testSubmitAnswer_forNumericInput_wrongAnswer_returnsOutcomeWithTransition() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    startTrainingSession(TEST_SKILL_ID_LIST_2)

    val result =
      questionAssessmentProgressController.submitAnswer(createNumericInputAnswer(2.0))
    result.observeForever(mockAsyncAnswerOutcomeObserver)
    testCoroutineDispatchers.runCurrent()

    // Verify that the answer submission failed as expected.
    verify(
      mockAsyncAnswerOutcomeObserver,
      atLeastOnce()
    ).onChanged(asyncAnswerOutcomeCaptor.capture())
    val answerOutcome = asyncAnswerOutcomeCaptor.value.getOrThrow()
    assertThat(answerOutcome.isCorrectAnswer).isFalse()
    assertThat(answerOutcome.feedback.html).isEmpty()
  }

  @Test
  fun testGetCurrentQuestion_thirdQuestion_isTerminalQuestion() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    val currentQuestionLiveData =
      questionAssessmentProgressController.getCurrentQuestion().toLiveData()
    currentQuestionLiveData.observeForever(mockCurrentQuestionLiveDataObserver)
    startTrainingSession(TEST_SKILL_ID_LIST_2)
    submitNumericInputAnswerAndMoveToNextQuestion(3.0)
    submitNumericInputAnswerAndMoveToNextQuestion(5.0)

    submitMultipleChoiceAnswerAndMoveToNextQuestion(1)

    // Verify that the third state is terminal.
    verify(mockCurrentQuestionLiveDataObserver, atLeastOnce()).onChanged(
      currentQuestionResultCaptor.capture()
    )
    assertThat(currentQuestionResultCaptor.value.isSuccess()).isTrue()
    val currentQuestion = currentQuestionResultCaptor.value.getOrThrow()
    assertThat(currentQuestion.currentQuestionIndex).isEqualTo(3)
    assertThat(currentQuestion.totalQuestionCount).isEqualTo(3)
    assertThat(currentQuestion.ephemeralState.stateTypeCase).isEqualTo(TERMINAL_STATE)
  }

  @Test
  fun testMoveToNext_onFinalQuestion_failsWithError() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    startTrainingSession(TEST_SKILL_ID_LIST_2)
    submitNumericInputAnswerAndMoveToNextQuestion(3.0)
    submitNumericInputAnswerAndMoveToNextQuestion(5.0)
    submitMultipleChoiceAnswerAndMoveToNextQuestion(1)

    val moveToStateResult =
      questionAssessmentProgressController.moveToNextQuestion()
    moveToStateResult.observeForever(mockAsyncNullableResultLiveDataObserver)
    testCoroutineDispatchers.runCurrent()

    // Verify we can't navigate past the last state of the training session.
    verify(mockAsyncNullableResultLiveDataObserver, atLeastOnce()).onChanged(
      asyncNullableResultCaptor.capture()
    )
    assertThat(asyncNullableResultCaptor.value.isFailure()).isTrue()
    assertThat(asyncNullableResultCaptor.value.getErrorOrNull())
      .hasMessageThat()
      .contains("Cannot navigate to next state; at most recent state.")
  }

  @Test
  fun testGetCurrentQuestion_afterPlayingSecondSession_returnsTerminalQuestion() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    val currentQuestionLiveData =
      questionAssessmentProgressController.getCurrentQuestion().toLiveData()
    currentQuestionLiveData.observeForever(mockCurrentQuestionLiveDataObserver)

    startTrainingSession(TEST_SKILL_ID_LIST_01)
    submitMultipleChoiceAnswerAndMoveToNextQuestion(1) // question 1
    submitNumericInputAnswerAndMoveToNextQuestion(3.0) // question 2
    submitTextInputAnswerAndMoveToNextQuestion("1/2") // question 3

    // Verify that we're now on the final state.
    verify(mockCurrentQuestionLiveDataObserver, atLeastOnce()).onChanged(
      currentQuestionResultCaptor.capture()
    )
    assertThat(currentQuestionResultCaptor.value.isSuccess()).isTrue()
    val currentQuestion = currentQuestionResultCaptor.value.getOrThrow()
    assertThat(currentQuestion.ephemeralState.stateTypeCase).isEqualTo(TERMINAL_STATE)
  }

  @Test
  fun testGetCurrentQuestion_afterPlayingThroughPrevSessions_returnsQuestionFromSecondSession() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    val currentQuestionLiveData =
      questionAssessmentProgressController.getCurrentQuestion().toLiveData()
    currentQuestionLiveData.observeForever(mockCurrentQuestionLiveDataObserver)
    playThroughSessionWithSkillList2()

    startTrainingSession(TEST_SKILL_ID_LIST_01)
    submitTextInputAnswerAndMoveToNextQuestion("1/4") // question 0
    submitMultipleChoiceAnswerAndMoveToNextQuestion(1) // question 1

    // Verify that we're on the second-to-last state of the second session.
    verify(mockCurrentQuestionLiveDataObserver, atLeastOnce()).onChanged(
      currentQuestionResultCaptor.capture()
    )
    assertThat(currentQuestionResultCaptor.value.isSuccess()).isTrue()
    val currentQuestion = currentQuestionResultCaptor.value.getOrThrow()
    assertThat(currentQuestion.currentQuestionIndex).isEqualTo(2)
    assertThat(currentQuestion.totalQuestionCount).isEqualTo(3)
    assertThat(currentQuestion.ephemeralState.stateTypeCase).isEqualTo(PENDING_STATE)
    // This question is not in the other test session.
    assertThat(currentQuestion.ephemeralState.state.content.html)
      .contains("What fraction does 'half'")
  }

  @Test
  fun testMoveToNext_onFinalQuestion_failsWithError_logsException() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    startTrainingSession(TEST_SKILL_ID_LIST_2)
    submitNumericInputAnswerAndMoveToNextQuestion(3.0)
    submitNumericInputAnswerAndMoveToNextQuestion(5.0)
    submitMultipleChoiceAnswerAndMoveToNextQuestion(1)

    val moveToStateResult =
      questionAssessmentProgressController.moveToNextQuestion()
    moveToStateResult.observeForever(mockAsyncNullableResultLiveDataObserver)
    testCoroutineDispatchers.runCurrent()
    val exception = fakeExceptionLogger.getMostRecentException()

    assertThat(exception).isInstanceOf(IllegalStateException::class.java)
    assertThat(exception).hasMessageThat()
      .contains("Cannot navigate to next state; at most recent state.")
  }

  @Test
  fun testSubmitAnswer_beforePlaying_failsWithError_logsException() {
    setUpTestApplicationWithSeed(questionSeed = 0)

    val result =
      questionAssessmentProgressController.submitAnswer(createMultipleChoiceAnswer(0))
    result.observeForever(mockAsyncAnswerOutcomeObserver)
    testCoroutineDispatchers.runCurrent()
    val exception = fakeExceptionLogger.getMostRecentException()

    assertThat(exception).isInstanceOf(IllegalStateException::class.java)
    assertThat(exception)
      .hasMessageThat()
      .contains("Cannot submit an answer if a training session has not yet begun.")
  }

  @Test
  fun testSubmitAnswer_forTextInput_wrongAnswer_returnsDefaultOutcome_showHint() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    startTrainingSession(TEST_SKILL_ID_LIST_2)

    val result =
      questionAssessmentProgressController.submitAnswer(createNumericInputAnswer(2.0))
    result.observeForever(mockAsyncAnswerOutcomeObserver)
    testCoroutineDispatchers.runCurrent()

    // Verify that the answer submission failed as expected.
    verify(
      mockAsyncAnswerOutcomeObserver,
      atLeastOnce()
    ).onChanged(asyncAnswerOutcomeCaptor.capture())
    val answerOutcome = asyncAnswerOutcomeCaptor.value.getOrThrow()
    assertThat(answerOutcome.isCorrectAnswer).isFalse()
    assertThat(answerOutcome.feedback.html).isEmpty()

    val currentQuestionLiveData =
      questionAssessmentProgressController.getCurrentQuestion().toLiveData()
    currentQuestionLiveData.observeForever(mockCurrentQuestionLiveDataObserver)
    testCoroutineDispatchers.runCurrent()

    verify(
      mockCurrentQuestionLiveDataObserver,
      atLeastOnce()
    ).onChanged(currentQuestionResultCaptor.capture())
    assertThat(currentQuestionResultCaptor.value.isSuccess()).isTrue()
    val ephemeralQuestion = currentQuestionResultCaptor.value.getOrThrow()

    assertThat(ephemeralQuestion.ephemeralState.stateTypeCase).isEqualTo(PENDING_STATE)
    assertThat(ephemeralQuestion.ephemeralState.pendingState.wrongAnswerCount).isEqualTo(1)

    val hintAndSolution = ephemeralQuestion.ephemeralState.state.interaction.getHint(0)
    assertThat(hintAndSolution.hintContent.html).contains("Hint text will appear here")
  }

  @Test
  fun testRevealHint_forWrongAnswer_showHint_returnHintIsRevealed() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    val currentQuestionLiveData =
      questionAssessmentProgressController.getCurrentQuestion().toLiveData()
    currentQuestionLiveData.observeForever(mockCurrentQuestionLiveDataObserver)
    playThroughSessionWithSkillList2()

    startTrainingSession(TEST_SKILL_ID_LIST_01)
    submitTextInputAnswerAndMoveToNextQuestion("1/4") // question 0
    submitMultipleChoiceAnswerAndMoveToNextQuestion(2) // question 1

    // Verify that we're on the second-to-last state of the second session.
    verify(mockCurrentQuestionLiveDataObserver, atLeastOnce()).onChanged(
      currentQuestionResultCaptor.capture()
    )
    assertThat(currentQuestionResultCaptor.value.isSuccess()).isTrue()
    val currentQuestion = currentQuestionResultCaptor.value.getOrThrow()
    assertThat(currentQuestion.currentQuestionIndex).isEqualTo(1)
    assertThat(currentQuestion.ephemeralState.stateTypeCase).isEqualTo(PENDING_STATE)
    // This question is not in the other test session.
    assertThat(currentQuestion.ephemeralState.state.content.html)
      .contains("If we talk about wanting")

    verify(
      mockCurrentQuestionLiveDataObserver,
      atLeastOnce()
    ).onChanged(currentQuestionResultCaptor.capture())
    assertThat(currentQuestionResultCaptor.value.isSuccess()).isTrue()
    val ephemeralQuestion = currentQuestionResultCaptor.value.getOrThrow()

    assertThat(ephemeralQuestion.ephemeralState.stateTypeCase).isEqualTo(PENDING_STATE)
    assertThat(ephemeralQuestion.ephemeralState.pendingState.wrongAnswerCount)
      .isEqualTo(1)

    val hintAndSolution = ephemeralQuestion.ephemeralState.state.interaction.getHint(0)
    assertThat(hintAndSolution.hintContent.html).contains("Hint text will appear here")

    val result = questionAssessmentProgressController.submitHintIsRevealed(
      ephemeralQuestion.ephemeralState.state,
      /* hintIsRevealed= */ true,
      /* hintIndex= */ 0
    )
    result.observeForever(mockAsyncHintObserver)
    testCoroutineDispatchers.runCurrent()

    // Verify that the current state updates. Hint revealed is true.
    verify(
      mockCurrentQuestionLiveDataObserver,
      atLeastOnce()
    ).onChanged(currentQuestionResultCaptor.capture())
    assertThat(currentQuestionResultCaptor.value.isSuccess()).isTrue()
    val updatedState = currentQuestionResultCaptor.value.getOrThrow()
    assertThat(updatedState.ephemeralState.state.interaction.getHint(0).hintIsRevealed)
      .isTrue()
  }

  @Test
  fun testRevealSolution_forWrongAnswer_showSolution_returnSolutionIsRevealed() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    val currentQuestionLiveData =
      questionAssessmentProgressController.getCurrentQuestion().toLiveData()
    currentQuestionLiveData.observeForever(mockCurrentQuestionLiveDataObserver)
    playThroughSessionWithSkillList2()

    startTrainingSession(TEST_SKILL_ID_LIST_01)
    submitTextInputAnswerAndMoveToNextQuestion("1/3") // question 0 (wrong answer)

    verify(mockCurrentQuestionLiveDataObserver, atLeastOnce()).onChanged(
      currentQuestionResultCaptor.capture()
    )
    assertThat(currentQuestionResultCaptor.value.isSuccess()).isTrue()
    val currentQuestion = currentQuestionResultCaptor.value.getOrThrow()
    assertThat(currentQuestion.ephemeralState.stateTypeCase).isEqualTo(PENDING_STATE)
    assertThat(currentQuestion.ephemeralState.pendingState.wrongAnswerCount).isEqualTo(1)

    val hintAndSolution = currentQuestion.ephemeralState.state.interaction.solution
    assertThat(hintAndSolution.correctAnswer.correctAnswer).contains("1/4")

    val result = questionAssessmentProgressController.submitSolutionIsRevealed(
      currentQuestion.ephemeralState.state
    )
    result.observeForever(mockAsyncSolutionObserver)
    testCoroutineDispatchers.runCurrent()

    // Verify that the current state updates. Hint revealed is true.
    verify(
      mockCurrentQuestionLiveDataObserver,
      atLeastOnce()
    ).onChanged(currentQuestionResultCaptor.capture())
    assertThat(currentQuestionResultCaptor.value.isSuccess()).isTrue()
    val updatedState = currentQuestionResultCaptor.value.getOrThrow()
    assertThat(updatedState.ephemeralState.state.interaction.solution.solutionIsRevealed).isTrue()
  }

  @Test
  fun testRevealedSolution_forWrongAnswer_returnScore2OutOf3() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    startTrainingSession(TEST_SKILL_ID_LIST_2)

    // Question 2
    // Submit question 2 wrong answer
    submitIncorrectAnswerForQuestion2(4.0)
    viewSolutionForQuestion2()
    submitCorrectAnswerForQuestion2()

    // Question 5
    submitCorrectAnswerForQuestion5()

    // Question 4
    submitCorrectAnswerForQuestion4()

    val userAssessmentPerformance = getExpectedGrade(TEST_SKILL_ID_LIST_2)
    val grade = FractionGrade.newBuilder().apply {
      pointsReceived = 2.0
      totalPointsAvailable = 3.0
    }.build()
    assertThat(userAssessmentPerformance.totalFractionScore).isEqualTo(grade)
    assertThat(userAssessmentPerformance.fractionScorePerSkillMappingCount).isEqualTo(1)
    assertThat(userAssessmentPerformance.getFractionScorePerSkillMappingOrThrow(TEST_SKILL_ID_2))
      .isEqualTo(grade)
  }

  @Test
  fun testRevealedHintAndSolution_forWrongAnswer_returnScore2OutOf3() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    startTrainingSession(TEST_SKILL_ID_LIST_2)

    // Question 2
    // Submit question 2 wrong answer
    submitIncorrectAnswerForQuestion2(4.0)
    viewHintForQuestion2()
    viewSolutionForQuestion2()
    submitCorrectAnswerForQuestion2()

    // Question 5
    submitCorrectAnswerForQuestion5()

    // Question 4
    submitCorrectAnswerForQuestion4()

    val userAssessmentPerformance = getExpectedGrade(TEST_SKILL_ID_LIST_2)
    val grade = FractionGrade.newBuilder().apply {
      pointsReceived = 2.0
      totalPointsAvailable = 3.0
    }.build()
    assertThat(userAssessmentPerformance.totalFractionScore).isEqualTo(grade)
    assertThat(userAssessmentPerformance.fractionScorePerSkillMappingCount).isEqualTo(1)
    assertThat(userAssessmentPerformance.getFractionScorePerSkillMappingOrThrow(TEST_SKILL_ID_2))
      .isEqualTo(grade)
  }

  @Test
  fun testRevealedHint_for5WrongAnswers_returnScore2Point4OutOf3() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    startTrainingSession(TEST_SKILL_ID_LIST_2)

    // Question 2
    // Submit question 2 wrong answers
    submitIncorrectAnswerForQuestion2(4.0)
    submitIncorrectAnswerForQuestion2(4.0)
    submitIncorrectAnswerForQuestion2(4.0)
    submitIncorrectAnswerForQuestion2(4.0)
    submitIncorrectAnswerForQuestion2(4.0)
    viewHintForQuestion2()
    submitCorrectAnswerForQuestion2()

    // Question 5
    submitCorrectAnswerForQuestion5()

    // Question 4
    submitCorrectAnswerForQuestion4()

    val userAssessmentPerformance = getExpectedGrade(TEST_SKILL_ID_LIST_2)
    val grade = FractionGrade.newBuilder().apply {
      pointsReceived = 2.4
      totalPointsAvailable = 3.0
    }.build()
    assertThat(userAssessmentPerformance.totalFractionScore).isEqualTo(grade)
    assertThat(userAssessmentPerformance.fractionScorePerSkillMappingCount).isEqualTo(1)
    assertThat(userAssessmentPerformance.getFractionScorePerSkillMappingOrThrow(TEST_SKILL_ID_2))
      .isEqualTo(grade)
  }

  @Test
  fun noHints_noWrongAnswers_noSolutionsViewed_returnPerfectScore() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    startTrainingSession(TEST_SKILL_ID_LIST_2)

    // Question 2
    submitCorrectAnswerForQuestion2()

    // Question 5
    submitCorrectAnswerForQuestion5()

    // Question 4
    submitCorrectAnswerForQuestion4()

    val userAssessmentPerformance = getExpectedGrade(TEST_SKILL_ID_LIST_2)
    val grade = FractionGrade.newBuilder().apply {
      pointsReceived = 3.0
      totalPointsAvailable = 3.0
    }.build()
    assertThat(userAssessmentPerformance.totalFractionScore).isEqualTo(grade)
    assertThat(userAssessmentPerformance.fractionScorePerSkillMappingCount).isEqualTo(1)
    assertThat(userAssessmentPerformance.getFractionScorePerSkillMappingOrThrow(TEST_SKILL_ID_2))
      .isEqualTo(grade)
  }

  @Test
  fun hintViewed_solutionViewed_wrongAnswersSubmitted_for2Skills_returnDifferingSkillScores() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    // This will generate question 1 (skill 0), question 2 (skill 0), and question 3 (skill 1)
    startTrainingSession(TEST_SKILL_ID_LIST_01)

    // Question 1
    // Submit question 1 wrong answers
    submitIncorrectAnswerForQuestion1(2)
    submitIncorrectAnswerForQuestion1(2)
    submitIncorrectAnswerForQuestion1(2)
    submitCorrectAnswerForQuestion1()

    // Question 2
    // Submit question 2 wrong answer
    submitIncorrectAnswerForQuestion2(4.0)
    viewHintForQuestion2()
    submitCorrectAnswerForQuestion2()

    // Question 3
    // Submit question 3 wrong answer
    submitIncorrectAnswerForQuestion3("3/4")
    viewSolutionForQuestion3()
    submitCorrectAnswerForQuestion3()

    val userAssessmentPerformance = getExpectedGrade(TEST_SKILL_ID_LIST_01)
    val totalScore = FractionGrade.newBuilder().apply {
      pointsReceived = 1.5
      totalPointsAvailable = 3.0
    }.build()
    val skill0Score = FractionGrade.newBuilder().apply {
      pointsReceived = 1.5
      totalPointsAvailable = 2.0
    }.build()
    val skill1Score = FractionGrade.newBuilder().apply {
      pointsReceived = 0.0
      totalPointsAvailable = 1.0
    }.build()
    assertThat(userAssessmentPerformance.totalFractionScore).isEqualTo(totalScore)
    assertThat(userAssessmentPerformance.fractionScorePerSkillMappingCount).isEqualTo(2)
    assertThat(userAssessmentPerformance.getFractionScorePerSkillMappingOrThrow(TEST_SKILL_ID_0))
      .isEqualTo(skill0Score)
    assertThat(userAssessmentPerformance.getFractionScorePerSkillMappingOrThrow(TEST_SKILL_ID_1))
      .isEqualTo(skill1Score)
  }

  @Test
  fun solutionViewedForAllQuestions_returnZeroScore() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    // This will generate question 1 (skill 0), question 2 (skill 0), and question 3 (skill 1)
    startTrainingSession(TEST_SKILL_ID_LIST_01)

    // Question 1
    // Submit question 1 wrong answer
    submitIncorrectAnswerForQuestion1(2)
    viewSolutionForQuestion1()
    submitCorrectAnswerForQuestion1()

    // Question 2
    // Submit question 2 wrong answer
    submitIncorrectAnswerForQuestion2(4.0)
    viewSolutionForQuestion2()
    submitCorrectAnswerForQuestion2()

    // Question 3
    // Submit question 3 wrong answer
    submitIncorrectAnswerForQuestion3("3/4")
    viewSolutionForQuestion3()
    submitCorrectAnswerForQuestion3()

    val userAssessmentPerformance = getExpectedGrade(TEST_SKILL_ID_LIST_01)
    val totalScore = FractionGrade.newBuilder().apply {
      pointsReceived = 0.0
      totalPointsAvailable = 3.0
    }.build()
    val skill0Score = FractionGrade.newBuilder().apply {
      pointsReceived = 0.0
      totalPointsAvailable = 2.0
    }.build()
    val skill1Score = FractionGrade.newBuilder().apply {
      pointsReceived = 0.0
      totalPointsAvailable = 1.0
    }.build()
    assertThat(userAssessmentPerformance.totalFractionScore).isEqualTo(totalScore)
    assertThat(userAssessmentPerformance.fractionScorePerSkillMappingCount).isEqualTo(2)
    assertThat(userAssessmentPerformance.getFractionScorePerSkillMappingOrThrow(TEST_SKILL_ID_0))
      .isEqualTo(skill0Score)
    assertThat(userAssessmentPerformance.getFractionScorePerSkillMappingOrThrow(TEST_SKILL_ID_1))
      .isEqualTo(skill1Score)
  }

  @Test
  fun hintViewed_for2QuestionsWithWrongAnswer_returnScore2Point6Outof3() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    // This will generate question 1 (skill 0), question 2 (skill 0), and question 3 (skill 1)
    startTrainingSession(TEST_SKILL_ID_LIST_01)

    // Question 1
    // Submit question 1 wrong answer
    submitIncorrectAnswerForQuestion1(2)
    viewHintForQuestion1(0)
    submitCorrectAnswerForQuestion1()

    // Question 2
    // Submit question 2 wrong answer
    submitIncorrectAnswerForQuestion2(4.0)
    viewHintForQuestion2()
    submitCorrectAnswerForQuestion2()

    // Question 3
    submitCorrectAnswerForQuestion3()

    val userAssessmentPerformance = getExpectedGrade(TEST_SKILL_ID_LIST_01)
    val totalScore = FractionGrade.newBuilder().apply {
      pointsReceived = 2.6
      totalPointsAvailable = 3.0
    }.build()
    val skill0Score = FractionGrade.newBuilder().apply {
      pointsReceived = 1.6
      totalPointsAvailable = 2.0
    }.build()
    val skill1Score = FractionGrade.newBuilder().apply {
      pointsReceived = 1.0
      totalPointsAvailable = 1.0
    }.build()
    assertThat(userAssessmentPerformance.totalFractionScore).isEqualTo(totalScore)
    assertThat(userAssessmentPerformance.fractionScorePerSkillMappingCount).isEqualTo(2)
    assertThat(userAssessmentPerformance.getFractionScorePerSkillMappingOrThrow(TEST_SKILL_ID_0))
      .isEqualTo(skill0Score)
    assertThat(userAssessmentPerformance.getFractionScorePerSkillMappingOrThrow(TEST_SKILL_ID_1))
      .isEqualTo(skill1Score)
  }

  @Test
  fun multipleHintsViewed_forQuestionsWithWrongAnswer_returnScore2Point7Outof3() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    // This will generate question 1 (skill 0), question 2 (skill 0), and question 3 (skill 1)
    startTrainingSession(TEST_SKILL_ID_LIST_01)

    // Question 1
    // Submit question 1 wrong answer
    submitIncorrectAnswerForQuestion1(2)
    viewHintForQuestion1(0)
    viewHintForQuestion1(1)
    submitCorrectAnswerForQuestion1()

    // Question 2
    submitCorrectAnswerForQuestion2()

    // Question 3
    submitCorrectAnswerForQuestion3()

    val userAssessmentPerformance = getExpectedGrade(TEST_SKILL_ID_LIST_01)
    val totalScore = FractionGrade.newBuilder().apply {
      pointsReceived = 2.7
      totalPointsAvailable = 3.0
    }.build()
    val skill0Score = FractionGrade.newBuilder().apply {
      pointsReceived = 1.7
      totalPointsAvailable = 2.0
    }.build()
    val skill1Score = FractionGrade.newBuilder().apply {
      pointsReceived = 1.0
      totalPointsAvailable = 1.0
    }.build()
    assertThat(userAssessmentPerformance.totalFractionScore).isEqualTo(totalScore)
    assertThat(userAssessmentPerformance.fractionScorePerSkillMappingCount).isEqualTo(2)
    assertThat(userAssessmentPerformance.getFractionScorePerSkillMappingOrThrow(TEST_SKILL_ID_0))
      .isEqualTo(skill0Score)
    assertThat(userAssessmentPerformance.getFractionScorePerSkillMappingOrThrow(TEST_SKILL_ID_1))
      .isEqualTo(skill1Score)
  }

  @Test
  fun solutionViewedForAllQuestions_returnMaxMasteryLossPerQuestion() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    // This will generate question 1 (skill 0), question 2 (skill 0), and question 3 (skill 1)
    startTrainingSession(TEST_SKILL_ID_LIST_01)

    // Question 1
    // Submit question 1 wrong answer
    submitIncorrectAnswerForQuestion1(2)
    viewSolutionForQuestion1()
    submitCorrectAnswerForQuestion1()

    // Question 2
    // Submit question 2 wrong answer
    submitIncorrectAnswerForQuestion2(4.0)
    viewSolutionForQuestion2()
    submitCorrectAnswerForQuestion2()

    // Question 3
    // Submit question 3 wrong answer
    submitIncorrectAnswerForQuestion3("3/4")
    viewSolutionForQuestion3()
    submitCorrectAnswerForQuestion3()

    val userAssessmentPerformance = getExpectedGrade(TEST_SKILL_ID_LIST_01)
    val skill0Mastery = -0.2
    val skill1Mastery = -0.1
    assertThat(userAssessmentPerformance.masteryPerSkillMappingCount).isEqualTo(2)
    assertThat(userAssessmentPerformance.getMasteryPerSkillMappingOrThrow(TEST_SKILL_ID_0))
      .isEqualTo(skill0Mastery)
    assertThat(userAssessmentPerformance.getMasteryPerSkillMappingOrThrow(TEST_SKILL_ID_1))
      .isEqualTo(skill1Mastery)
  }

  @Test
  fun correctAnswerOnFirstTryForAllQuestions_returnMaxMasteryGainPerQuestion() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    // This will generate question 1 (skill 0), question 2 (skill 0), and question 3 (skill 1)
    startTrainingSession(TEST_SKILL_ID_LIST_01)

    // Question 1
    submitCorrectAnswerForQuestion1()

    // Question 2
    submitCorrectAnswerForQuestion2()

    // Question 3
    submitCorrectAnswerForQuestion3()

    val userAssessmentPerformance = getExpectedGrade(TEST_SKILL_ID_LIST_01)
    val skill0Mastery = 0.2
    val skill1Mastery = 0.1
    assertThat(userAssessmentPerformance.masteryPerSkillMappingCount).isEqualTo(2)
    assertThat(userAssessmentPerformance.getMasteryPerSkillMappingOrThrow(TEST_SKILL_ID_0))
      .isEqualTo(skill0Mastery)
    assertThat(userAssessmentPerformance.getMasteryPerSkillMappingOrThrow(TEST_SKILL_ID_1))
      .isEqualTo(skill1Mastery)
  }

  @Test
  fun hintsAndSolutionsViewedWithWrongAnswers_noMisconceptions_returnDifferingMasteryDegrees() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    // This will generate question 1 (skill 0), question 2 (skill 0), and question 3 (skill 1)
    startTrainingSession(TEST_SKILL_ID_LIST_01)

    // Question 1
    // Submit question 1 wrong answers
    submitIncorrectAnswerForQuestion1(2)
    submitIncorrectAnswerForQuestion1(2)
    submitCorrectAnswerForQuestion1()

    // Question 2
    // Submit question 2 wrong answer
    submitIncorrectAnswerForQuestion2(4.0)
    viewHintForQuestion2()
    submitCorrectAnswerForQuestion2()

    // Question 3
    // Submit question 3 wrong answer
    submitIncorrectAnswerForQuestion3("3/4")
    viewSolutionForQuestion3()
    submitCorrectAnswerForQuestion3()

    val userAssessmentPerformance = getExpectedGrade(TEST_SKILL_ID_LIST_01)
    val skill0Mastery = 0.03
    val skill1Mastery = -0.1
    assertThat(userAssessmentPerformance.masteryPerSkillMappingCount).isEqualTo(2)
    assertThat(userAssessmentPerformance.getMasteryPerSkillMappingOrThrow(TEST_SKILL_ID_0))
      .isEqualTo(skill0Mastery)
    assertThat(userAssessmentPerformance.getMasteryPerSkillMappingOrThrow(TEST_SKILL_ID_1))
      .isEqualTo(skill1Mastery)
  }

  @Test
  fun maxMasteryLossPerQuestionSurpassed_returnMaxMasteryLossForQuestion() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    // This will generate question 1 (skill 0), question 2 (skill 0), and question 0 (skill 1)
    startTrainingSession(TEST_SKILL_ID_LIST_01)

    // Question 1
    submitCorrectAnswerForQuestion1()

    // Question 2
    submitCorrectAnswerForQuestion2()

    // Question 3
    // Submit question 3 wrong answers (surpass max mastery loss lower bound for this question)
    submitIncorrectAnswerForQuestion3("3/4")
    submitIncorrectAnswerForQuestion3("3/4")
    submitIncorrectAnswerForQuestion3("3/4")
    submitIncorrectAnswerForQuestion3("3/4")
    submitIncorrectAnswerForQuestion3("3/4")
    submitCorrectAnswerForQuestion3()

    val userAssessmentPerformance = getExpectedGrade(TEST_SKILL_ID_LIST_01)
    val skill0Mastery = 0.2
    val skill1Mastery = -0.1
    assertThat(userAssessmentPerformance.masteryPerSkillMappingCount).isEqualTo(2)
    assertThat(userAssessmentPerformance.getMasteryPerSkillMappingOrThrow(TEST_SKILL_ID_0))
      .isEqualTo(skill0Mastery)
    assertThat(userAssessmentPerformance.getMasteryPerSkillMappingOrThrow(TEST_SKILL_ID_1))
      .isEqualTo(skill1Mastery)
  }

  @Test
  fun multipleHintsViewed_forQuestionWithWrongAnswer_returnMastery0Point11ForLinkedSkill() {
    setUpTestApplicationWithSeed(questionSeed = 0)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    // This will generate question 1 (skill 0), question 2 (skill 0), and question 3 (skill 1)
    startTrainingSession(TEST_SKILL_ID_LIST_01)

    // Question 1
    // Submit question 1 wrong answer
    submitIncorrectAnswerForQuestion1(2)
    viewHintForQuestion1(0)
    viewHintForQuestion1(1)
    submitCorrectAnswerForQuestion1()

    // Question 2
    submitCorrectAnswerForQuestion2()

    // Question 3
    submitCorrectAnswerForQuestion3()

    val userAssessmentPerformance = getExpectedGrade(TEST_SKILL_ID_LIST_01)
    val skill0Mastery = 0.11
    val skill1Mastery = 0.1
    assertThat(userAssessmentPerformance.masteryPerSkillMappingCount).isEqualTo(2)
    assertThat(userAssessmentPerformance.getMasteryPerSkillMappingOrThrow(TEST_SKILL_ID_0))
      .isEqualTo(skill0Mastery)
    assertThat(userAssessmentPerformance.getMasteryPerSkillMappingOrThrow(TEST_SKILL_ID_1))
      .isEqualTo(skill1Mastery)
  }

  @Test
  fun wrongAnswersAllSubmittedWithMisconception_onlyMisconceptionSkillIdMasteryDegreesAffected() {
    setUpTestApplicationWithSeed(questionSeed = 1)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    // This will generate question 1 (skill 0), question 2 (skill 0), and question 0 (skill 0, 1)
    startTrainingSession(TEST_SKILL_ID_LIST_01)

    // Question 1
    submitCorrectAnswerForQuestion1()

    // Question 2
    submitCorrectAnswerForQuestion2()

    // Question 0
    // Submit question 0 wrong answers
    submitIncorrectAnswerForQuestion0("123/456")
    submitIncorrectAnswerForQuestion0("123/456")
    submitCorrectAnswerForQuestion0()

    val userAssessmentPerformance = getExpectedGrade(TEST_SKILL_ID_LIST_01)
    val skill0Mastery = 0.3
    val skill1Mastery = 0.0
    assertThat(userAssessmentPerformance.masteryPerSkillMappingCount).isEqualTo(2)
    assertThat(userAssessmentPerformance.getMasteryPerSkillMappingOrThrow(TEST_SKILL_ID_0))
      .isEqualTo(skill0Mastery)
    assertThat(userAssessmentPerformance.getMasteryPerSkillMappingOrThrow(TEST_SKILL_ID_1))
      .isEqualTo(skill1Mastery)
  }

  @Test
  fun someWrongAnswersSubmittedWithTaggedMisconceptionSkillId() {
    setUpTestApplicationWithSeed(questionSeed = 1)
    subscribeToCurrentQuestionToAllowSessionToLoad()
    // This will generate question 1 (skill 0), question 2 (skill 0), and question 0 (skill 0, 1)
    startTrainingSession(TEST_SKILL_ID_LIST_01)

    // Question 1
    submitCorrectAnswerForQuestion1()

    // Question 2
    submitCorrectAnswerForQuestion2()

    // Question 0
    // Submit question 0 wrong answers
    submitIncorrectAnswerForQuestion0("4/5")
    submitIncorrectAnswerForQuestion0("123/456")
    submitCorrectAnswerForQuestion0()

    val userAssessmentPerformance = getExpectedGrade(TEST_SKILL_ID_LIST_01)
    val skill0Mastery = 0.25
    val skill1Mastery = 0.0
    assertThat(userAssessmentPerformance.masteryPerSkillMappingCount).isEqualTo(2)
    assertThat(userAssessmentPerformance.getMasteryPerSkillMappingOrThrow(TEST_SKILL_ID_0))
      .isEqualTo(skill0Mastery)
    assertThat(userAssessmentPerformance.getMasteryPerSkillMappingOrThrow(TEST_SKILL_ID_1))
      .isEqualTo(skill1Mastery)
  }

  private fun setUpTestApplicationWithSeed(questionSeed: Long) {
    TestQuestionModule.questionSeed = questionSeed
    ApplicationProvider.getApplicationContext<TestApplication>().inject(this)
  }

  /**
   * Creates a blank subscription to the current state to ensure that requests to load the session
   * complete, otherwise post-load operations may fail. An observer is required since the current
   * LiveData-DataProvider interop implementation will only lazily load data based on whether
   * there's an active subscription.
   */
  private fun subscribeToCurrentQuestionToAllowSessionToLoad() {
    questionAssessmentProgressController.getCurrentQuestion().toLiveData()
      .observeForever(mockCurrentQuestionLiveDataObserver)
  }

  private fun subscribeToScoreAndMasteryCalculations(skillIdList: List<String>) {
    questionAssessmentProgressController.calculateScores(skillIdList).toLiveData()
      .observeForever(mockScoreAndMasteryLiveDataObserver)
  }

  private fun startTrainingSession(skillIdList: List<String>) {
    questionTrainingController.startQuestionTrainingSession(skillIdList)
    testCoroutineDispatchers.runCurrent()
  }

  private fun submitMultipleChoiceAnswer(choiceIndex: Int) {
    questionAssessmentProgressController.submitAnswer(createMultipleChoiceAnswer(choiceIndex))
    testCoroutineDispatchers.runCurrent()
  }

  private fun submitTextInputAnswer(textAnswer: String) {
    questionAssessmentProgressController.submitAnswer(createTextInputAnswer(textAnswer))
    testCoroutineDispatchers.runCurrent()
  }

  private fun submitNumericInputAnswer(numericAnswer: Double) {
    questionAssessmentProgressController.submitAnswer(createNumericInputAnswer(numericAnswer))
    testCoroutineDispatchers.runCurrent()
  }

  private fun submitMultipleChoiceAnswerAndMoveToNextQuestion(choiceIndex: Int) {
    submitMultipleChoiceAnswer(choiceIndex)
    moveToNextQuestion()
  }

  private fun submitTextInputAnswerAndMoveToNextQuestion(textAnswer: String) {
    submitTextInputAnswer(textAnswer)
    moveToNextQuestion()
  }

  private fun submitNumericInputAnswerAndMoveToNextQuestion(numericAnswer: Double) {
    submitNumericInputAnswer(numericAnswer)
    moveToNextQuestion()
  }

  private fun moveToNextQuestion() {
    questionAssessmentProgressController.moveToNextQuestion()
    testCoroutineDispatchers.runCurrent()
  }

  private fun endTrainingSession() {
    questionTrainingController.stopQuestionTrainingSession()
    testCoroutineDispatchers.runCurrent()
  }

  private fun playThroughSessionWithSkillList2() {
    startTrainingSession(TEST_SKILL_ID_LIST_2)
    submitNumericInputAnswerAndMoveToNextQuestion(3.0)
    submitNumericInputAnswerAndMoveToNextQuestion(5.0)
    submitMultipleChoiceAnswerAndMoveToNextQuestion(1)
    endTrainingSession()
  }

  private fun createMultipleChoiceAnswer(choiceIndex: Int): UserAnswer {
    return UserAnswer.newBuilder()
      .setAnswer(InteractionObject.newBuilder().setNonNegativeInt(choiceIndex))
      .setPlainAnswer(choiceIndex.toString())
      .build()
  }

  private fun createTextInputAnswer(textAnswer: String): UserAnswer {
    return UserAnswer.newBuilder()
      .setAnswer(InteractionObject.newBuilder().setNormalizedString(textAnswer).build())
      .setPlainAnswer(textAnswer)
      .build()
  }

  private fun createNumericInputAnswer(numericAnswer: Double): UserAnswer {
    return UserAnswer.newBuilder()
      .setAnswer(InteractionObject.newBuilder().setReal(numericAnswer).build())
      .setPlainAnswer(numericAnswer.toString())
      .build()
  }

  private fun submitCorrectAnswerForQuestion0() {
    submitTextInputAnswerAndMoveToNextQuestion("1/2")
  }

  private fun submitIncorrectAnswerForQuestion0(answer: String) {
    assertThat(answer).isNotEqualTo("1/2")
    submitTextInputAnswerAndMoveToNextQuestion(answer)
    verify(
      mockCurrentQuestionLiveDataObserver,
      atLeastOnce()
    ).onChanged(currentQuestionResultCaptor.capture())
  }

  private fun submitCorrectAnswerForQuestion1() {
    submitMultipleChoiceAnswerAndMoveToNextQuestion(1)
  }

  private fun submitIncorrectAnswerForQuestion1(answer: Int) {
    assertThat(answer).isNotEqualTo(1)
    submitMultipleChoiceAnswerAndMoveToNextQuestion(answer)
    verify(
      mockCurrentQuestionLiveDataObserver,
      atLeastOnce()
    ).onChanged(currentQuestionResultCaptor.capture())
  }

  private fun viewHintForQuestion1(index: Int) {
    val ephemeralQuestion = currentQuestionResultCaptor.value.getOrThrow()
    val hint = ephemeralQuestion.ephemeralState.state.interaction.getHint(index)
    if (index == 0) {
      assertThat(hint.hintContent.html).contains("<p>Hint text will appear here</p>")
    } else if (index == 1) {
      assertThat(hint.hintContent.html).contains("<p>Second hint text will appear here</p>")
    }
    questionAssessmentProgressController.submitHintIsRevealed(
      ephemeralQuestion.ephemeralState.state, true, index
    )
  }

  private fun viewSolutionForQuestion1() {
    val ephemeralQuestion = currentQuestionResultCaptor.value.getOrThrow()
    questionAssessmentProgressController.submitSolutionIsRevealed(
      ephemeralQuestion.ephemeralState.state
    )
  }

  private fun submitCorrectAnswerForQuestion2() {
    submitNumericInputAnswerAndMoveToNextQuestion(3.0)
  }

  private fun submitIncorrectAnswerForQuestion2(answer: Double) {
    assertThat(answer).isNotEqualTo(3.0)
    submitNumericInputAnswerAndMoveToNextQuestion(answer)
    verify(
      mockCurrentQuestionLiveDataObserver,
      atLeastOnce()
    ).onChanged(currentQuestionResultCaptor.capture())
  }

  private fun viewHintForQuestion2() {
    val ephemeralQuestion = currentQuestionResultCaptor.value.getOrThrow()
    val hint = ephemeralQuestion.ephemeralState.state.interaction.getHint(0)
    assertThat(hint.hintContent.html).contains("<p>Hint text will appear here</p>")
    questionAssessmentProgressController.submitHintIsRevealed(
      ephemeralQuestion.ephemeralState.state, true, 0
    )
  }

  private fun viewSolutionForQuestion2() {
    val ephemeralQuestion = currentQuestionResultCaptor.value.getOrThrow()
    val solution = ephemeralQuestion.ephemeralState.state.interaction.solution
    assertThat(solution.correctAnswer.correctAnswer).isEqualTo("3.0")
    questionAssessmentProgressController.submitSolutionIsRevealed(
      ephemeralQuestion.ephemeralState.state
    )
  }

  private fun submitCorrectAnswerForQuestion3() {
    submitTextInputAnswerAndMoveToNextQuestion("1/2")
  }

  private fun submitIncorrectAnswerForQuestion3(answer: String) {
    assertThat(answer).isNotEqualTo("1/2")
    submitTextInputAnswerAndMoveToNextQuestion(answer)
    verify(
      mockCurrentQuestionLiveDataObserver,
      atLeastOnce()
    ).onChanged(currentQuestionResultCaptor.capture())
  }

  private fun viewSolutionForQuestion3() {
    val ephemeralQuestion = currentQuestionResultCaptor.value.getOrThrow()
    val solution = ephemeralQuestion.ephemeralState.state.interaction.solution
    assertThat(solution.correctAnswer.correctAnswer).isEqualTo("1/2")
    questionAssessmentProgressController.submitSolutionIsRevealed(
      ephemeralQuestion.ephemeralState.state
    )
  }

  private fun submitCorrectAnswerForQuestion4() {
    submitMultipleChoiceAnswerAndMoveToNextQuestion(1)
  }

  private fun submitCorrectAnswerForQuestion5() {
    submitNumericInputAnswerAndMoveToNextQuestion(5.0)
  }

  private fun getExpectedGrade(skillIdList: List<String>): UserAssessmentPerformance {
    subscribeToScoreAndMasteryCalculations(skillIdList)
    testCoroutineDispatchers.runCurrent()
    verify(
      mockScoreAndMasteryLiveDataObserver,
      atLeastOnce()
    ).onChanged(performanceCalculationCaptor.capture())
    return performanceCalculationCaptor.value.getOrThrow()
  }

  // TODO(#89): Move this to a common test application component.
  @Module
  class TestModule {
    @Provides
    @Singleton
    fun provideContext(application: Application): Context {
      return application
    }

    // TODO(#59): Either isolate these to their own shared test module, or use the real logging
    // module in tests to avoid needing to specify these settings for tests.
    @EnableConsoleLog
    @Provides
    fun provideEnableConsoleLog(): Boolean = true

    @EnableFileLog
    @Provides
    fun provideEnableFileLog(): Boolean = false

    @GlobalLogLevel
    @Provides
    fun provideGlobalLogLevel(): LogLevel = LogLevel.VERBOSE
  }

  @Module
  class TestQuestionModule {
    companion object {
      var questionSeed = 0L
    }

    @Provides
    @QuestionCountPerTrainingSession
    fun provideQuestionCountPerTrainingSession(): Int = 3

    @Provides
    @QuestionTrainingSeed
    fun provideQuestionTrainingSeed(): Long = questionSeed

    @Provides
    @ViewHintScorePenalty
    fun provideViewHintScorePenalty(): Int = 1

    @Provides
    @WrongAnswerScorePenalty
    fun provideWrongAnswerScorePenalty(): Int = 1

    @Provides
    @MaxScorePerQuestion
    fun provideMaxScorePerQuestion(): Int = 10

    @Provides
    @InternalScoreMultiplyFactor
    fun provideInternalScoreMultiplyFactor(): Int = 10

    @Provides
    @MaxMasteryGainPerQuestion
    fun provideMaxMasteryGainPerQuestion(): Int = 10

    @Provides
    @MaxMasteryLossPerQuestion
    fun provideMaxMasteryLossPerQuestion(): Int = -10

    @Provides
    @ViewHintMasteryPenalty
    fun provideViewHintMasteryPenalty(): Int = 2

    @Provides
    @WrongAnswerMasteryPenalty
    fun provideWrongAnswerMasteryPenalty(): Int = 5

    @Provides
    @InternalMasteryMultiplyFactor
    fun provideInternalMasteryMultiplyFactor(): Int = 100
  }

  // TODO(#89): Move this to a common test application component.
  @Singleton
  @Component(
    modules = [
      TestModule::class, TestQuestionModule::class, ContinueModule::class,
      FractionInputModule::class, ItemSelectionInputModule::class, MultipleChoiceInputModule::class,
      NumberWithUnitsRuleModule::class, NumericInputRuleModule::class, TextInputRuleModule::class,
      InteractionsModule::class, DragDropSortInputModule::class, TestLogReportingModule::class,
      ImageClickInputModule::class, LogStorageModule::class, TestDispatcherModule::class,
      RatioInputModule::class, RobolectricModule::class, FakeOppiaClockModule::class,
      CachingTestModule::class, NetworkConnectionUtilDebugModule::class
    ]
  )
  interface TestApplicationComponent : DataProvidersInjector {
    @Component.Builder
    interface Builder {
      @BindsInstance
      fun setApplication(application: Application): Builder

      fun build(): TestApplicationComponent
    }

    fun inject(controllerTest: QuestionAssessmentProgressControllerTest)
  }

  class TestApplication : Application(), DataProvidersInjectorProvider {
    private val component: TestApplicationComponent by lazy {
      DaggerQuestionAssessmentProgressControllerTest_TestApplicationComponent.builder()
        .setApplication(this)
        .build()
    }

    fun inject(controllerTest: QuestionAssessmentProgressControllerTest) {
      component.inject(controllerTest)
    }

    override fun getDataProvidersInjector(): DataProvidersInjector = component
  }
}
