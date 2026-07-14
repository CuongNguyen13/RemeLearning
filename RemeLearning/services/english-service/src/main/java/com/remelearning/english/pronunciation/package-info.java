/**
 * Pronunciation domain module: persists recurring pronunciation issues derived from
 * ai-service's {@code learning.gap.analyzed} event (filtered to category = "pronunciation"),
 * classified by sound/aspect (vowel, consonant, stress, ...) and exposed read-only via REST.
 * Deliberately has no {@code TranscriptReadyConsumer}/{@code TranscriptService} of its own:
 * transcripts are a cross-domain concern already persisted once by vocabulary's consumer into
 * the shared {@code transcripts}/{@code transcript_segments} tables, so a second ingestion path
 * here would just be redundant writes to the same rows.
 */
package com.remelearning.english.pronunciation;
