package com.startup.goHappy.entities.model;


import lombok.Data;

import java.util.List;

@Data
public class Trip {
	@DocumentId
	private String id;
	private int cost;
	private String startTime;
	private String endTime;
	private List<String> coverImages;
	private List<String> participantList;
	private Integer seatsLeft=30;
	private String location;
	private String title;
	private String description;
	private List<String> hotels;

	@Override
	public String toString() {
		return "Trip{" +
				"id='" + id + '\'' +
				", cost=" + cost +
				", startTime='" + startTime + '\'' +
				", endTime='" + endTime + '\'' +
				", coverImages=" + coverImages +
				", participantList=" + participantList +
				", seatsLeft=" + seatsLeft +
				", location='" + location + '\'' +
				", title='" + title + '\'' +
				", description='" + description + '\'' +
				", hotels=" + hotels +
				'}';
	}
}
