package com.ggutim.lupus.web;

import com.ggutim.lupus.room.exception.InvalidRulesetException;
import com.ggutim.lupus.room.exception.MasterTokenMismatchException;
import com.ggutim.lupus.room.exception.NicknameTakenException;
import com.ggutim.lupus.room.exception.PlayerNotFoundException;
import com.ggutim.lupus.room.exception.RoomAlreadyStartedException;
import com.ggutim.lupus.room.exception.RoomFullException;
import com.ggutim.lupus.room.exception.RoomNotFoundException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(
                HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors));
    }

    @ExceptionHandler(InvalidRulesetException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRuleset(InvalidRulesetException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(
                HttpStatus.BAD_REQUEST, ex.getMessage(), null));
    }

    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRoomNotFound(RoomNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(
                HttpStatus.NOT_FOUND, ex.getMessage(), null));
    }

    @ExceptionHandler(PlayerNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePlayerNotFound(PlayerNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(
                HttpStatus.NOT_FOUND, ex.getMessage(), null));
    }

    @ExceptionHandler(RoomFullException.class)
    public ResponseEntity<Map<String, Object>> handleRoomFull(RoomFullException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(
                HttpStatus.CONFLICT, ex.getMessage(), null));
    }

    @ExceptionHandler(RoomAlreadyStartedException.class)
    public ResponseEntity<Map<String, Object>> handleRoomAlreadyStarted(RoomAlreadyStartedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(
                HttpStatus.CONFLICT, ex.getMessage(), null));
    }

    @ExceptionHandler(NicknameTakenException.class)
    public ResponseEntity<Map<String, Object>> handleNicknameTaken(NicknameTakenException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(
                HttpStatus.CONFLICT, ex.getMessage(), null));
    }

    @ExceptionHandler(MasterTokenMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMasterTokenMismatch(MasterTokenMismatchException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body(
                HttpStatus.FORBIDDEN, ex.getMessage(), null));
    }

    private Map<String, Object> body(HttpStatus status, String message, Map<String, String> fieldErrors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            body.put("fieldErrors", fieldErrors);
        }
        return body;
    }
}
