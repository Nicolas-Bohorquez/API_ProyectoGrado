package com.example.AppSegurity.DTO;

import com.example.AppSegurity.Enums.AccionTomada;
import com.example.AppSegurity.Enums.CategoriaProceso;
import com.example.AppSegurity.Enums.ClaseAlerta;
import com.example.AppSegurity.Enums.NivelRiesgo;
import com.example.AppSegurity.Enums.TipoEvidenciaVision;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class NuevaAlertaRequest {
    //Parametros de la clase
    private ClaseAlerta claseAlerta; // VISION, AUDIO, PROCESO  o TECLADO
    private NivelRiesgo nivelRiesgo;
    
    //Campos de una alerta de tipo Vision
    private TipoEvidenciaVision tipoEvidenciaVision;
    private Integer cantidadRostros;
    private String objetoDetectado;
    private Double confianzaIa;
    private String urlFotoWebcam;
    private String urlCapturaPantalla;
    
    //Hashes de integridad de evidencias (E2EE)
    private String hashWebcam;
    private String hashPantalla;
    private String hashAudio;
    
    
    
    //Campos de una alerta de tipo audio
    private String transcripcion;
    private Integer vocesDetectadas;
    private Double confianzaVoz;
    private String urlAudio;
    
    //Campos de una alerta de tipo proceso
    private Integer pidProceso;
    private String nombreProceso;
    private CategoriaProceso categoriaProceso;
    private AccionTomada accionTomada;
    
    //Explicación en texto de la alerta (opcional, de cualquier clase): qué ocurrió y por qué se avisa. Se recorta al guardar.
    private String detalle;

    //Campos de una alerta de tipo teclado
    private String combinacionTeclas;
    private String patronSospechoso;
    
    //Constructor sin parametros
    public NuevaAlertaRequest(){
        
    }
}
