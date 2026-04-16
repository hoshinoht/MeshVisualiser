package llm

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"sync"
	"time"

	"github.com/inf2007/inf2007-team07-2026/server/internal/platform"
)

// Config holds runtime-updatable LLM connection settings.
type Config struct {
	mu      sync.RWMutex
	BaseURL string `json:"llm_base_url"`
	Model   string `json:"llm_model"`
	APIKey  string `json:"llm_api_key,omitempty"`
}

type GenerationOptions struct {
	MaxTokens   int
	Temperature float64
	Caller      string
}

const defaultTemperature = 0.7

func NewConfig() *Config {
	cfg := &Config{
		BaseURL: "http://localhost:1234",
		Model:   "qwen/qwen3.5-9b",
	}
	if v := os.Getenv("LLM_BASE_URL"); v != "" {
		cfg.BaseURL = v
	}
	if v := os.Getenv("LLM_MODEL"); v != "" {
		cfg.Model = v
	}
	if v := os.Getenv("LLM_API_KEY"); v != "" {
		cfg.APIKey = v
	}
	return cfg
}

func (c *Config) Get() (baseURL, model, apiKey string) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.BaseURL, c.Model, c.APIKey
}

func (c *Config) Update(baseURL, model, apiKey string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if baseURL != "" {
		c.BaseURL = baseURL
	}
	if model != "" {
		c.Model = model
	}
	if apiKey != "" {
		c.APIKey = apiKey
	}
}

// OpenAI-compatible chat completion types.

var llmHTTPClient = &http.Client{}

type ChatMessage struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

type chatRequest struct {
	Model       string        `json:"model"`
	Messages    []ChatMessage `json:"messages"`
	MaxTokens   int           `json:"max_tokens"`
	Temperature float64       `json:"temperature"`
}

type chatChoice struct {
	Message ChatMessage `json:"message"`
}

type chatUsage struct {
	PromptTokens     int `json:"prompt_tokens"`
	CompletionTokens int `json:"completion_tokens"`
	TotalTokens      int `json:"total_tokens"`
}

type chatResponse struct {
	Choices []chatChoice `json:"choices"`
	Usage   *chatUsage   `json:"usage,omitempty"`
}

// isPrivateIP returns true if the IP is in a private/reserved range (RFC1918, link-local, loopback).
func isPrivateIP(ip net.IP) bool {
	privateRanges := []string{
		"10.0.0.0/8",
		"172.16.0.0/12",
		"192.168.0.0/16",
		"127.0.0.0/8",
		"169.254.0.0/16",
		"::1/128",
		"fc00::/7",
		"fe80::/10",
	}
	for _, cidr := range privateRanges {
		_, network, err := net.ParseCIDR(cidr)
		if err != nil {
			continue
		}
		if network.Contains(ip) {
			return true
		}
	}
	return false
}

// ValidateBaseURL checks that the URL has http/https scheme and does not point to a private IP.
func ValidateBaseURL(rawURL string) error {
	u, err := url.Parse(rawURL)
	if err != nil {
		return fmt.Errorf("invalid URL: %w", err)
	}
	if u.Scheme != "http" && u.Scheme != "https" {
		return fmt.Errorf("URL scheme must be http or https, got %q", u.Scheme)
	}
	host := u.Hostname()
	ip := net.ParseIP(host)
	if ip != nil && isPrivateIP(ip) {
		return fmt.Errorf("private/reserved IP addresses are not allowed")
	}
	if ip == nil {
		addrs, err := net.LookupIP(host)
		if err != nil {
			return fmt.Errorf("cannot resolve host %q: %w", host, err)
		}
		for _, addr := range addrs {
			if isPrivateIP(addr) {
				return fmt.Errorf("host %q resolves to private IP %s", host, addr)
			}
		}
	}
	return nil
}

// callLLM sends a chat completion request using the legacy defaults.
func callLLM(ctx context.Context, cfg *Config, messages []ChatMessage, maxTokens int, callers ...string) (string, error) {
	caller := "unknown"
	if len(callers) > 0 {
		caller = callers[0]
	}
	return callLLMWithOptions(ctx, cfg, messages, GenerationOptions{
		MaxTokens:   maxTokens,
		Temperature: defaultTemperature,
		Caller:      caller,
	})
}

// callLLMWithOptions sends a chat completion request and logs prompt/response details.
// The caller field identifies which endpoint triggered the call (e.g. "narrate", "quiz").
func callLLMWithOptions(ctx context.Context, cfg *Config, messages []ChatMessage, opts GenerationOptions) (string, error) {
	caller := opts.Caller
	if caller == "" {
		caller = "unknown"
	}
	temperature := opts.Temperature
	if temperature < 0 {
		temperature = defaultTemperature
	}

	baseURL, model, apiKey := cfg.Get()

	// Compute prompt stats for logging
	var systemChars, userChars, totalMsgs int
	for _, m := range messages {
		totalMsgs++
		switch m.Role {
		case "system":
			systemChars += len(m.Content)
		case "user":
			userChars += len(m.Content)
		}
	}

	platform.Logf("[llm:%s] calling model=%s msgs=%d system=%d_chars user=%d_chars max_tokens=%d temp=%.2f",
		caller, model, totalMsgs, systemChars, userChars, opts.MaxTokens, temperature)

	body := chatRequest{
		Model:       model,
		Messages:    messages,
		MaxTokens:   opts.MaxTokens,
		Temperature: temperature,
	}
	payload, err := json.Marshal(body)
	if err != nil {
		return "", fmt.Errorf("marshal request: %w", err)
	}

	start := time.Now()

	req, err := http.NewRequestWithContext(ctx, "POST", baseURL+"/v1/chat/completions", bytes.NewReader(payload))
	if err != nil {
		return "", fmt.Errorf("create request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	if apiKey != "" {
		req.Header.Set("Authorization", "Bearer "+apiKey)
	}

	resp, err := llmHTTPClient.Do(req)
	elapsed := time.Since(start)
	if err != nil {
		platform.Logf("[llm:%s] FAILED after %s: %v", caller, elapsed.Round(time.Millisecond), err)
		return "", fmt.Errorf("llm request failed: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		platform.Logf("[llm:%s] read error after %s: %v", caller, elapsed.Round(time.Millisecond), err)
		return "", fmt.Errorf("read response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		platform.Logf("[llm:%s] HTTP %d after %s: %s", caller, resp.StatusCode, elapsed.Round(time.Millisecond), string(respBody[:min(len(respBody), 200)]))
		return "", fmt.Errorf("llm returned %d: %s", resp.StatusCode, string(respBody[:min(len(respBody), 200)]))
	}

	var cr chatResponse
	if err := json.Unmarshal(respBody, &cr); err != nil {
		platform.Logf("[llm:%s] parse error after %s: %v", caller, elapsed.Round(time.Millisecond), err)
		return "", fmt.Errorf("parse response: %w", err)
	}
	if len(cr.Choices) == 0 {
		platform.Logf("[llm:%s] no choices after %s", caller, elapsed.Round(time.Millisecond))
		return "", fmt.Errorf("no choices in response")
	}

	content := cr.Choices[0].Message.Content
	if cr.Usage != nil {
		if cr.Usage.CompletionTokens > 0 && elapsed > 0 {
			tokensPerSecond := float64(cr.Usage.CompletionTokens) / elapsed.Seconds()
			platform.Logf("[llm:%s] OK %s response=%d_chars tokens prompt=%d completion=%d total=%d tok/s=%.1f",
				caller,
				elapsed.Round(time.Millisecond),
				len(content),
				cr.Usage.PromptTokens,
				cr.Usage.CompletionTokens,
				cr.Usage.TotalTokens,
				tokensPerSecond,
			)
		} else {
			platform.Logf("[llm:%s] OK %s response=%d_chars tokens prompt=%d completion=%d total=%d",
				caller,
				elapsed.Round(time.Millisecond),
				len(content),
				cr.Usage.PromptTokens,
				cr.Usage.CompletionTokens,
				cr.Usage.TotalTokens,
			)
		}
	} else {
		platform.Logf("[llm:%s] OK %s response=%d_chars usage=unavailable", caller, elapsed.Round(time.Millisecond), len(content))
	}
	return content, nil
}
