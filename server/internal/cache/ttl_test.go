package cache

import (
	"testing"
	"time"
)

func testCache(ttl time.Duration) (*TTLCache[string], chan struct{}) {
	stop := make(chan struct{})
	return NewTTLCache[string](ttl, stop), stop
}

func TestCacheSetGet(t *testing.T) {
	c, stop := testCache(time.Minute)
	defer close(stop)

	c.Set("k1", "v1")

	if got, ok := c.Get("k1"); !ok || got != "v1" {
		t.Fatalf("Get(k1) = (%q, %v), want (%q, true)", got, ok, "v1")
	}

	if got, ok := c.Get("missing"); ok || got != "" {
		t.Fatalf("Get(missing) = (%q, %v), want (\"\", false)", got, ok)
	}
}

func TestCacheExpiry(t *testing.T) {
	c, stop := testCache(50 * time.Millisecond)
	defer close(stop)

	c.Set("k1", "v1")
	if _, ok := c.Get("k1"); !ok {
		t.Fatal("expected key to be present immediately after Set")
	}

	time.Sleep(80 * time.Millisecond)
	if got, ok := c.Get("k1"); ok || got != "" {
		t.Fatalf("Get(k1) after expiry = (%q, %v), want (\"\", false)", got, ok)
	}
}

func TestCacheEvict(t *testing.T) {
	c, stop := testCache(50 * time.Millisecond)
	defer close(stop)

	c.Set("k1", "v1")
	c.Set("k2", "v2")
	c.Set("k3", "v3")

	time.Sleep(80 * time.Millisecond)
	c.evict()

	if got := c.Len(); got != 0 {
		t.Fatalf("Len() = %d, want 0", got)
	}
}

func TestCacheLen(t *testing.T) {
	c, stop := testCache(time.Minute)
	defer close(stop)

	c.Set("k1", "v1")
	c.Set("k2", "v2")
	c.Set("k3", "v3")

	if got := c.Len(); got != 3 {
		t.Fatalf("Len() = %d, want 3", got)
	}

	c.Set("k4", "v4")
	if got := c.Len(); got != 4 {
		t.Fatalf("Len() = %d, want 4", got)
	}
}

func TestCacheKeyDeterminism(t *testing.T) {
	ab := HashKey("a", "b")
	if got := HashKey("a", "b"); got != ab {
		t.Fatalf("hashKey(a,b) = %q, want %q", got, ab)
	}
	if got := HashKey("b", "a"); got == ab {
		t.Fatal("hashKey(a,b) should differ from hashKey(b,a)")
	}
	if got := HashKey("ab", ""); got == ab {
		t.Fatal("hashKey(a,b) should differ from hashKey(ab,\"\")")
	}
}
