package com.mukho.stomp.controller;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Controller;

import com.mukho.stomp.dto.ReqDto;
import com.mukho.stomp.dto.ResDto;
import com.mukho.stomp.dto.ResSessionsDto;
import com.mukho.stomp.listener.StompEventListener;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * 실제 코드에서는 Service를 호출하여 비즈니스 로직을 처리하고 결과를 반환한다.
 * 단, 이 예제에서는 간단한 예제를 위해 Service를 사용하지 않고 Controller에서 처리한다.
 */
@Controller
@Slf4j
public class StompController {

	private final TaskScheduler taskScheduler;

	private final StompEventListener eventListener;

	private final SimpMessagingTemplate	messagingTemplate;

	private final ConcurrentHashMap<String, ScheduledFuture<?>> sessionMap = new ConcurrentHashMap<>();

	public StompController(TaskScheduler taskScheduler, StompEventListener eventListener, SimpMessagingTemplate messagingTemplate) {
		this.taskScheduler = taskScheduler;
		this.eventListener = eventListener;
		this.messagingTemplate = messagingTemplate;
	}

	@MessageMapping("/hello") // 수신 /app/hello
	@SendTo({"/topic/hello", "/topic/hello2"}) // 송신
	public ResDto basic(ReqDto reqDto, Message<ReqDto> message, MessageHeaders headers) { // Body, 전체, Header Payloads
		log.info("reqDto : {}", reqDto);
		log.info("message : {}", message);
		log.info("headers : {}", headers);

		return new ResDto(reqDto.getMessage().toUpperCase(), LocalDateTime.now());
	}

	// @PathVariable
	@MessageMapping("/hello/{detail}") // 수신 /app/hello/xxx
	@SendTo({"/topic/hello", "/topic/hello2"}) // 송신
	public ResDto detail(ReqDto reqDto, @DestinationVariable("detail") String detail) {
		log.info("reqDto : {}", reqDto);

		return new ResDto(reqDto.getMessage().toUpperCase(), LocalDateTime.now());
	}

	// SendToUser: 특정 세션에게 전달
	@MessageMapping("/sessions") // app/sessions
	@SendToUser("/queue/sessions") // 만약 내가 채팅방에 몇명이 있는지 서버에 요청하는 경우
	public ResSessionsDto detail(ReqDto reqDto, MessageHeaders headers) {
		log.info("reqDto : {}", reqDto);
		String sessionId = headers.get("simpleSessionId").toString();
		log.info("simpleSessionId : {}", sessionId);

		Set<String> sessions = eventListener.getSessions();

		return new ResSessionsDto(sessions.size(), sessions.stream().toList(), sessionId, LocalDateTime.now());
	}

	// Programmatically Send
	@MessageMapping("/code1") // /app/code1
	public void code1(ReqDto reqDto, Message<ReqDto> message, MessageHeaders headers) {
		log.info("reqDto : {}", reqDto);
		log.info("message : {}", message);
		log.info("headers : {}", headers);

		ResDto resDto = new ResDto(reqDto.getMessage().toUpperCase(), LocalDateTime.now());
		messagingTemplate.convertAndSend("/topic/hello", resDto);
	}

	@MessageMapping("/code2") // /app/code2
	public void code2(ReqDto reqDto, MessageHeaders headers) {
		log.info("reqDto : {}", reqDto);
		String sessionId = headers.get("simpleSessionId").toString();
		log.info("simpleSessionId : {}", sessionId);

		Set<String> sessions = eventListener.getSessions();
		ResSessionsDto resSessionsDto = new ResSessionsDto(sessions.size(), sessions.stream().toList(), sessionId, LocalDateTime.now());

		messagingTemplate.convertAndSendToUser(sessionId, "/queue/sessions", resSessionsDto, createHeaders(sessionId));
	}

	MessageHeaders createHeaders(@Nullable String sessionId) {
		SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
		headerAccessor.setSessionId(sessionId);
		headerAccessor.setLeaveMutable(true);
		return headerAccessor.getMessageHeaders();
	}

	@MessageMapping("/start") // /app/start
	public void start(ReqDto reqDto, MessageHeaders headers) {
		log.info("reqDto : {}", reqDto);
		String sessionId = headers.get("simpleSessionId").toString();
		log.info("simpleSessionId : {}", sessionId);

		ScheduledFuture<?> scheduleFuture = taskScheduler.scheduleAtFixedRate(() -> {
			Random random = new Random();
			int currentPrice = random.nextInt(100);
			messagingTemplate.convertAndSendToUser(sessionId, "/queue/sessions", currentPrice, createHeaders(sessionId));
		}, Duration.ofSeconds(3));
		sessionMap.put(sessionId, scheduleFuture);

		// Thread.sleep(10000);
		// scheduledFuture.cancel(true);
	}

	@MessageMapping("/stop") // /app/stop
	public void stop(ReqDto reqDto, MessageHeaders headers) {
		log.info("reqDto : {}", reqDto);
		String sessionId = headers.get("simpleSessionId").toString();
		log.info("simpleSessionId : {}", sessionId);

		ScheduledFuture<?> remove = sessionMap.remove(sessionId);
		remove.cancel(true);
	}

	@MessageMapping("/exception")
	@SendTo("/topic/hello")
	public void exception(ReqDto request, MessageHeaders headers) throws Exception {
		log.info("request: {}", request);
		String message = request.getMessage();
		switch(message) {
			case "runtime":
				throw new RuntimeException();
			case "nullPointer":
				throw new NullPointerException();
			case "io":
				throw new IOException();
			case "exception":
				throw new Exception();
			default:
				throw new InvalidParameterException();
		}
	}
}
