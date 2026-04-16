package quiz

import (
	"reflect"
	"sync"
	"testing"
)

func TestPickStaticQuestions_Count(t *testing.T) {
	if got := len(PickStaticQuestions(10)); got != 10 {
		t.Fatalf("len(PickStaticQuestions(10)) = %d, want 10", got)
	}
	if got := len(PickStaticQuestions(1000)); got != len(staticQuestionPool) {
		t.Fatalf("len(PickStaticQuestions(1000)) = %d, want %d", got, len(staticQuestionPool))
	}
	if got := len(PickStaticQuestions(0)); got != 0 {
		t.Fatalf("len(PickStaticQuestions(0)) = %d, want 0", got)
	}
}

func TestPickStaticQuestions_CorrectAnswerTracking(t *testing.T) {
	questions := PickStaticQuestions(10)
	for i, q := range questions {
		if q.Correct < 0 || q.Correct >= len(q.Options) {
			t.Fatalf("question %d has invalid Correct index %d for %d options", i, q.Correct, len(q.Options))
		}
		if q.Options[q.Correct] == "" {
			t.Fatalf("question %d has empty correct option", i)
		}
	}
}

func TestPickStaticQuestions_NoGlobalMutation(t *testing.T) {
	baseline := make([][]string, 5)
	for i := 0; i < 5; i++ {
		baseline[i] = append([]string(nil), staticQuestionPool[i].Options...)
	}

	for i := 0; i < 10; i++ {
		_ = PickStaticQuestions(10)
	}

	for i := 0; i < 5; i++ {
		if !reflect.DeepEqual(staticQuestionPool[i].Options, baseline[i]) {
			t.Fatalf("staticQuestionPool[%d].Options mutated: got %v, want %v", i, staticQuestionPool[i].Options, baseline[i])
		}
	}
}

func TestPickStaticQuestions_ConcurrentSafety(t *testing.T) {
	var wg sync.WaitGroup
	for i := 0; i < 20; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			questions := PickStaticQuestions(10)
			if len(questions) != 10 {
				t.Errorf("len(PickStaticQuestions(10)) = %d, want 10", len(questions))
			}
		}()
	}
	wg.Wait()
}
