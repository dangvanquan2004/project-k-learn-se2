package com.klearn.controller;

import com.klearn.dto.ApiResponse;
import com.klearn.dto.CreateRoomRequest;
import com.klearn.dto.SpeakingRoomParticipantDto;
import com.klearn.model.SpeakingRoom;
import com.klearn.security.UserDetailsImpl;
import com.klearn.service.SpeakingRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/speaking-rooms")
@RequiredArgsConstructor
public class SpeakingRoomApiController {

    private final SpeakingRoomService speakingRoomService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SpeakingRoom>>> listActiveRooms() {
        return ResponseEntity.ok(
            ApiResponse.success("Success", speakingRoomService.listActiveRooms())
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SpeakingRoom>> createRoom(
            @RequestBody CreateRoomRequest request,
            @AuthenticationPrincipal UserDetailsImpl user
    ) {
        if (user == null) return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        if (request == null || request.getRoomName() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Room name is required"));
        }

        SpeakingRoom room = speakingRoomService.createRoom(
                request.getRoomName(),
                request.getMaxParticipants(),
                request.getDescription(),
                user.getUser()
        );

        broadcastParticipants(room.getRoomId());
        return ResponseEntity.ok(ApiResponse.success("Success", room));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<ApiResponse<Void>> joinRoom(
        @PathVariable("id") Long roomId,
        @AuthenticationPrincipal UserDetailsImpl user
    ) {
        if (user == null) return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));

        speakingRoomService.joinRoom(roomId, user.getUser());
        broadcastParticipants(roomId);
        return ResponseEntity.ok(ApiResponse.success("Joined", null));
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveRoom(
        @PathVariable("id") Long roomId,
        @AuthenticationPrincipal UserDetailsImpl user
    ) {
        if (user == null) return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));

        speakingRoomService.leaveRoom(roomId, user.getUser());
        broadcastParticipants(roomId);
        return ResponseEntity.ok(ApiResponse.success("Left", null));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(
            @PathVariable("id") Long roomId,
            @AuthenticationPrincipal UserDetailsImpl user
    ) {
        if (user == null) return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));

        speakingRoomService.deleteRoom(roomId, user.getUserId());

        // Tuỳ chọn: Broadcast một tin nhắn để các user khác biết phòng đã đóng và tự động văng ra
        messagingTemplate.convertAndSend("/topic/speaking-room/" + roomId + "/deleted", "DELETED");

        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    private void broadcastParticipants(Long roomId) {
        List<SpeakingRoomParticipantDto> participants = speakingRoomService.listParticipants(roomId);
        messagingTemplate.convertAndSend("/topic/speaking-room/" + roomId + "/participants", participants);
    }


}

