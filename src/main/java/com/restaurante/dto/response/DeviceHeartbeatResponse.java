package com.restaurante.dto.response;

import com.restaurante.model.enums.DispositivoStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceHeartbeatResponse {
    private String status;
    private LocalDateTime serverTime;
    private DispositivoStatus dispositivoStatus;
}

