package httpapi

import "testing"

func TestSummaryCacheKey(t *testing.T) {
	score := 3
	total := 5

	base := SummaryRequest{MeshState: "connected", QuizScore: &score, QuizTotal: &total}
	same := SummaryRequest{MeshState: "connected", QuizScore: &score, QuizTotal: &total}
	differentState := SummaryRequest{MeshState: "discovering", QuizScore: &score, QuizTotal: &total}
	nilQuiz := SummaryRequest{MeshState: "connected"}

	baseKey := SummaryCacheKey(base)
	if got := SummaryCacheKey(same); got != baseKey {
		t.Fatalf("SummaryCacheKey(same) = %q, want %q", got, baseKey)
	}
	if got := SummaryCacheKey(differentState); got == baseKey {
		t.Fatal("SummaryCacheKey should differ for different MeshState")
	}
	if got := SummaryCacheKey(nilQuiz); got == baseKey {
		t.Fatal("SummaryCacheKey should differ for nil vs non-nil quiz score")
	}
}

func TestQuizCacheKey(t *testing.T) {
	key1 := QuizCacheKey("state1")
	if got := QuizCacheKey("state1"); got != key1 {
		t.Fatalf("QuizCacheKey(state1) = %q, want %q", got, key1)
	}
	if got := QuizCacheKey("state2"); got == key1 {
		t.Fatal("QuizCacheKey(state1) should differ from QuizCacheKey(state2)")
	}
}
