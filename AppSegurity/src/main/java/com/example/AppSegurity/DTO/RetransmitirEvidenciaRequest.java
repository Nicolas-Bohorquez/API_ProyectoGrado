package com.example.AppSegurity.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RetransmitirEvidenciaRequest {
    private String urlFotoWebcam;
    private String urlCapturaPantalla;
    private String urlAudio;
}
