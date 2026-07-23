package com.remelearning.english.speaking.library.dto;

/** One catalog topic as seen by a learner: identity/level plus this learner's gating status. */
public class SpeakingLibraryTopicDto {
	private Long id;
	private String name;
	private String level;
	private String status;

	public SpeakingLibraryTopicDto(Long id, String name, String level, String status) {
		this.id = id;
		this.name = name;
		this.level = level;
		this.status = status;
	}

	public Long getId() { return id; }
	public String getName() { return name; }
	public String getLevel() { return level; }
	public String getStatus() { return status; }
}
