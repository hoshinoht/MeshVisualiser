package llm

import (
	"context"
	"errors"
	"fmt"
	"sync"
	"testing"
	"time"

	"github.com/inf2007/inf2007-team07-2026/server/internal/testutil"
)

func TestQueue_SingleRequest(t *testing.T) {
	srv, _ := testutil.FakeLLM(0, "result")
	defer srv.Close()

	queue := NewQueue(&Config{BaseURL: srv.URL, Model: "test-model"}, 1, 3, time.Second)
	got, err := queue.Call(context.Background(), "k1", []ChatMessage{{Role: "user", Content: "hi"}}, 10, "test")
	if err != nil {
		t.Fatalf("Call error = %v", err)
	}
	if got != "result" {
		t.Fatalf("result = %q, want %q", got, "result")
	}
}

func TestQueue_Semaphore(t *testing.T) {
	srv, _ := testutil.FakeLLM(200*time.Millisecond, "ok")
	defer srv.Close()

	queue := NewQueue(&Config{BaseURL: srv.URL, Model: "test-model"}, 1, 3, 2*time.Second)
	start := time.Now()

	var wg sync.WaitGroup
	errs := make(chan error, 2)
	for i := range 2 {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			_, err := queue.Call(context.Background(), fmt.Sprintf("k%d", i), []ChatMessage{{Role: "user", Content: "hi"}}, 10, "test")
			errs <- err
		}(i)
	}
	wg.Wait()
	close(errs)

	for err := range errs {
		if err != nil {
			t.Fatalf("Call error = %v", err)
		}
	}
	if elapsed := time.Since(start); elapsed < 400*time.Millisecond {
		t.Fatalf("elapsed = %v, want >= 400ms", elapsed)
	}
}

func TestQueue_BackpressureFull(t *testing.T) {
	srv, _ := testutil.FakeLLM(2*time.Second, "ok")
	defer srv.Close()

	queue := NewQueue(&Config{BaseURL: srv.URL, Model: "test-model"}, 1, 2, 5*time.Second)
	var wg sync.WaitGroup
	errs := make(chan error, 4)

	for i := range 3 {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			_, err := queue.Call(context.Background(), fmt.Sprintf("pre-%d", i), []ChatMessage{{Role: "user", Content: "hi"}}, 10, "test")
			errs <- err
		}(i)
	}

	time.Sleep(50 * time.Millisecond)

	wg.Add(1)
	go func() {
		defer wg.Done()
		_, err := queue.Call(context.Background(), "overflow", []ChatMessage{{Role: "user", Content: "hi"}}, 10, "test")
		errs <- err
	}()

	wg.Wait()
	close(errs)

	var full int
	for err := range errs {
		if errors.Is(err, ErrQueueFull) {
			full++
			continue
		}
		if err != nil {
			t.Fatalf("unexpected error = %v", err)
		}
	}
	if full != 1 {
		t.Fatalf("ErrQueueFull count = %d, want 1", full)
	}
}

func TestQueue_Singleflight(t *testing.T) {
	srv, count := testutil.FakeLLM(200*time.Millisecond, "shared")
	defer srv.Close()

	queue := NewQueue(&Config{BaseURL: srv.URL, Model: "test-model"}, 1, 5, 2*time.Second)
	var wg sync.WaitGroup
	results := make(chan string, 4)
	errs := make(chan error, 4)

	for range 4 {
		wg.Add(1)
		go func() {
			defer wg.Done()
			got, err := queue.Call(context.Background(), "same-key", []ChatMessage{{Role: "user", Content: "hi"}}, 10, "test")
			results <- got
			errs <- err
		}()
	}
	wg.Wait()
	close(results)
	close(errs)

	for err := range errs {
		if err != nil {
			t.Fatalf("Call error = %v", err)
		}
	}
	for got := range results {
		if got != "shared" {
			t.Fatalf("result = %q, want shared", got)
		}
	}
	if got := count.Load(); got != 1 {
		t.Fatalf("LLM calls = %d, want 1", got)
	}
}

func TestQueue_DifferentKeys(t *testing.T) {
	srv, count := testutil.FakeLLM(0, "resp")
	defer srv.Close()

	queue := NewQueue(&Config{BaseURL: srv.URL, Model: "test-model"}, 2, 3, time.Second)
	var wg sync.WaitGroup
	for i := range 3 {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			_, _ = queue.Call(context.Background(), fmt.Sprintf("k%d", i), []ChatMessage{{Role: "user", Content: "hi"}}, 10, "test")
		}(i)
	}
	wg.Wait()

	if got := count.Load(); got != 3 {
		t.Fatalf("LLM calls = %d, want 3", got)
	}
}

func TestQueue_ContextCancelWhileQueued(t *testing.T) {
	srv, _ := testutil.FakeLLM(2*time.Second, "slow")
	defer srv.Close()

	queue := NewQueue(&Config{BaseURL: srv.URL, Model: "test-model"}, 1, 3, 5*time.Second)
	go func() {
		_, _ = queue.Call(context.Background(), "first", []ChatMessage{{Role: "user", Content: "hi"}}, 10, "test")
	}()

	time.Sleep(50 * time.Millisecond)

	ctx, cancel := context.WithCancel(context.Background())
	go func() {
		time.Sleep(100 * time.Millisecond)
		cancel()
	}()

	_, err := queue.Call(ctx, "second", []ChatMessage{{Role: "user", Content: "hi"}}, 10, "test")
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("error = %v, want context.Canceled", err)
	}
}

func TestQueue_Timeout(t *testing.T) {
	srv, _ := testutil.FakeLLM(5*time.Second, "slow")
	defer srv.Close()

	queue := NewQueue(&Config{BaseURL: srv.URL, Model: "test-model"}, 1, 3, 100*time.Millisecond)
	_, err := queue.Call(context.Background(), "k1", []ChatMessage{{Role: "user", Content: "hi"}}, 10, "test")
	if !errors.Is(err, context.DeadlineExceeded) {
		t.Fatalf("error = %v, want context.DeadlineExceeded", err)
	}
}

func TestQueue_Stats(t *testing.T) {
	srv, _ := testutil.FakeLLM(1*time.Second, "slow")
	defer srv.Close()

	queue := NewQueue(&Config{BaseURL: srv.URL, Model: "test-model"}, 1, 3, 2*time.Second)
	done := make(chan struct{})
	go func() {
		defer close(done)
		_, _ = queue.Call(context.Background(), "k1", []ChatMessage{{Role: "user", Content: "hi"}}, 10, "test")
	}()

	deadline := time.Now().Add(200 * time.Millisecond)
	for {
		active, _ := queue.Stats()
		if active == 1 {
			break
		}
		if time.Now().After(deadline) {
			t.Fatal("active never became 1")
		}
		time.Sleep(10 * time.Millisecond)
	}

	<-done
	active, _ := queue.Stats()
	if active != 0 {
		t.Fatalf("active = %d, want 0", active)
	}
}
