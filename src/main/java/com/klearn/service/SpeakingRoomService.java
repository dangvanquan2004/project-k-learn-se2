package com.klearn.service;

import com.klearn.dto.SpeakingRoomParticipantDto;
import com.klearn.model.RoomParticipant;
import com.klearn.model.SpeakingRoom;
import com.klearn.model.User;
import com.klearn.repository.RoomParticipantRepository;
import com.klearn.repository.SpeakingRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpeakingRoomService {

    private final SpeakingRoomRepository speakingRoomRepository;
    private final RoomParticipantRepository roomParticipantRepository;

    public List<SpeakingRoom> listActiveRooms() {
        return speakingRoomRepository.findByIsActiveTrue();
    }

    public SpeakingRoom createRoom(String name, Integer maxParticipants, String description, User createdBy) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Room name is required");
        }
        int max = (maxParticipants != null && maxParticipants > 0) ? maxParticipants : 10;

        SpeakingRoom room = new SpeakingRoom();
        room.setName(name.trim());
        room.setMaxParticipants(max);
        room.setCreatedBy(createdBy);
        room.setIsActive(true);
        room.setDescription(description);

        SpeakingRoom saved = speakingRoomRepository.save(room);

        // Auto-join creator
        RoomParticipant rp = new RoomParticipant();
        rp.setRoom(saved);
        rp.setUser(createdBy);
        roomParticipantRepository.save(rp);

        return saved;
    }

    public void joinRoom(Long roomId, User user) {
        SpeakingRoom room = speakingRoomRepository.findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        if (Boolean.FALSE.equals(room.getIsActive())) {
            throw new IllegalArgumentException("Room is not active");
        }

        Optional<RoomParticipant> existing = roomParticipantRepository.findByRoom_RoomIdAndUser_UserId(roomId, user.getUserId());
        if (existing.isPresent()) return;

        // Capacity check
        long participantsCount = roomParticipantRepository.findByRoom_RoomId(roomId).size();
        if (participantsCount >= room.getMaxParticipants()) {
            throw new IllegalArgumentException("Room is full");
        }

        RoomParticipant rp = new RoomParticipant();
        rp.setRoom(room);
        rp.setUser(user);
        roomParticipantRepository.save(rp);
    }

    @Transactional
    public void leaveRoom(Long roomId, User user) {
        Optional<RoomParticipant> existing = roomParticipantRepository.findByRoom_RoomIdAndUser_UserId(roomId, user.getUserId());

        if (existing.isPresent()) {
            roomParticipantRepository.delete(existing.get());

            // Cập nhật lại trạng thái phòng nếu không còn ai
            long remainingParticipants = roomParticipantRepository.findByRoom_RoomId(roomId).size() - 1; // Trừ đi người vừa rời
            if (remainingParticipants <= 0) {
                SpeakingRoom room = speakingRoomRepository.findById(roomId).orElse(null);
                if (room != null) {
                    room.setIsActive(false); // Tự động đóng phòng khi trống
                    speakingRoomRepository.save(room);
                }
            }
        }
    }
    @Transactional
    public void deleteRoom(Long roomId, Long userId) {
        SpeakingRoom room = speakingRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        // Kiểm tra quyền: Chỉ người tạo mới được xoá
        if (!room.getCreatedBy().getUserId().equals(userId)) {
            throw new IllegalArgumentException("Chỉ chủ phòng mới có quyền xoá phòng");
        }

        // Vô hiệu hóa phòng thay vì xoá cứng khỏi DB để giữ lịch sử
        room.setIsActive(false);
        speakingRoomRepository.save(room);
    }

    public List<SpeakingRoomParticipantDto> listParticipants(Long roomId) {
        return roomParticipantRepository.findByRoom_RoomId(roomId).stream()
            .sorted((a, b) -> {
                var at = a.getJoinedAt();
                var bt = b.getJoinedAt();
                if (at == null && bt == null) return 0;
                if (at == null) return 1;
                if (bt == null) return -1;
                return at.compareTo(bt);
            })
            .map(rp -> {
                SpeakingRoomParticipantDto dto = new SpeakingRoomParticipantDto();
                dto.setUserId(rp.getUser() != null ? rp.getUser().getUserId() : null);
                dto.setUserName(rp.getUser() != null ? rp.getUser().getName() : null);
                return dto;
            })
            .collect(Collectors.toList());
    }
}

