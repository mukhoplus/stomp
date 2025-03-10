package com.mukho.stomp.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class ResDto {
	private String message;
	private LocalDateTime localDateTime;
}
