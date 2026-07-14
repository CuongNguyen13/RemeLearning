/**
 * Grammar domain module: persists recurring grammar mistakes derived from ai-service's
 * {@code learning.gap.analyzed} event (filtered to category = "grammar"), classified by
 * rule type (tense, agreement, articles, ...) and exposed read-only via REST.
 * Deliberately has no {@code TranscriptReadyConsumer}/{@code TranscriptService} of its own:
 * transcripts are a cross-domain concern already persisted once by vocabulary's consumer into
 * the shared {@code transcripts}/{@code transcript_segments} tables, so a second ingestion path
 * here would just be redundant writes to the same rows.
 */
package com.remelearning.english.grammar;
