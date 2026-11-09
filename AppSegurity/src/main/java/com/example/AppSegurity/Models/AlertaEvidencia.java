package com.example.AppSegurity.Models;

import com.example.AppSegurity.Enums.ClaseAlerta;
import com.example.AppSegurity.Enums.NivelRiesgo;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;


@Getter
@Setter
@Document(collection = "alerta_evidencia")
public class AlertaEvidencia {
    //Parametros de la clase
    @Id
    private String idAlerta;
    private String sesionId;
    private ClaseAlerta claseAlerta;
    private LocalDateTime horaCaptura;
    private NivelRiesgo nivelRiesgo;
    // Explicación en texto de la alerta (opcional). Las alertas antiguas no la tienen.
    private String detalle;
    
    //Hashes de integridad de evidencias (E2EE)
    private String hashWebcam;
    private String hashPantalla;
    private String hashAudio;
    
    
    @org.springframework.data.annotation.Transient
    private String nombreEstudiante;
    
    //Constructor sin parametros
    @org.springframework.data.annotation.Transient
    private String base64WebcamTransient;
    @org.springframework.data.annotation.Transient
    private String base64PantallaTransient;
    @org.springframework.data.annotation.Transient
    private String base64AudioTransient;

    public String getBase64WebcamTransient() { return base64WebcamTransient; }
    public void setBase64WebcamTransient(String b) { this.base64WebcamTransient = b; }
    public String getBase64PantallaTransient() { return base64PantallaTransient; }
    public void setBase64PantallaTransient(String b) { this.base64PantallaTransient = b; }
    public String getBase64AudioTransient() { return base64AudioTransient; }
    public void setBase64AudioTransient(String b) { this.base64AudioTransient = b; }

    public AlertaEvidencia(){
        
    }
    
    //Constructor con parametros

    public AlertaEvidencia(String idAlerta, String sesionId, ClaseAlerta claseAlerta, LocalDateTime horaCaptura, NivelRiesgo nivelRiesgo) {
        this.idAlerta = idAlerta;
        this.sesionId = sesionId;
        this.claseAlerta = claseAlerta;
        this.horaCaptura = horaCaptura;
        this.nivelRiesgo = nivelRiesgo;
    }
    
}
