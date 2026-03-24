package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"sync"
	"time"
)

// LLMConfig holds runtime-updatable LLM connection settings.
type LLMConfig struct {
	mu      sync.RWMutex
	BaseURL string `json:"llm_base_url"`
	Model   string `json:"llm_model"`
	APIKey  string `json:"llm_api_key,omitempty"`
}

func NewLLMConfig() *LLMConfig {
	cfg := &LLMConfig{
		BaseURL: "http://localhost:1234",
		Model:   "default",
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

func (c *LLMConfig) Get() (baseURL, model, apiKey string) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.BaseURL, c.Model, c.APIKey
}

func (c *LLMConfig) Update(baseURL, model, apiKey string) {
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

var llmHTTPClient = &http.Client{Timeout: 60 * time.Second}

type chatMessage struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

type chatRequest struct {
	Model       string        `json:"model"`
	Messages    []chatMessage `json:"messages"`
	MaxTokens   int           `json:"max_tokens"`
	Temperature float64       `json:"temperature"`
}

type chatChoice struct {
	Message chatMessage `json:"message"`
}

type chatResponse struct {
	Choices []chatChoice `json:"choices"`
}

func callLLM(cfg *LLMConfig, messages []chatMessage, maxTokens int) (string, error) {
	baseURL, model, apiKey := cfg.Get()

	body := chatRequest{
		Model:       model,
		Messages:    messages,
		MaxTokens:   maxTokens,
		Temperature: 0.7,
	}
	payload, err := json.Marshal(body)
	if err != nil {
		return "", fmt.Errorf("marshal request: %w", err)
	}

	req, err := http.NewRequest("POST", baseURL+"/v1/chat/completions", bytes.NewReader(payload))
	if err != nil {
		return "", fmt.Errorf("create request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	if apiKey != "" {
		req.Header.Set("Authorization", "Bearer "+apiKey)
	}

	resp, err := llmHTTPClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("llm request failed: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return "", fmt.Errorf("read response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("llm returned %d: %s", resp.StatusCode, string(respBody[:min(len(respBody), 200)]))
	}

	var cr chatResponse
	if err := json.Unmarshal(respBody, &cr); err != nil {
		return "", fmt.Errorf("parse response: %w", err)
	}
	if len(cr.Choices) == 0 {
		return "", fmt.Errorf("no choices in response")
	}
	return cr.Choices[0].Message.Content, nil
}
