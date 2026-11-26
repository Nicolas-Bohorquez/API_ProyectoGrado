package com.example.AppSegurity.DTO;

import com.example.AppSegurity.Enums.AccionTomada;
import com.example.AppSegurity.Enums.CategoriaProceso;
import com.example.AppSegurity.Enums.ClaseAlerta;
import com.example.AppSegurity.Enums.NivelRiesgo;
import com.example.AppSegurity.Enums.TipoEvidenciaVision;
import jakarta.validation.constraints.Size;
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
    @Size(max = 500)
    private String objetoDetectado;
    private Double confianzaIa;
    @Size(max = 7000000)
    private String urlFotoWebcam;
    @Size(max = 7000000)
    private String urlCapturaPantalla;
    
    //Hashes de integridad de evidencias (E2EE)
    @Size(max = 500)
    private String hashWebcam;
    @Size(max = 500)
    private String hashPantalla;
    @Size(max = 500)
    private String hashAudio;
    
    
    
    //Campos de una alerta de tipo audio
    @Size(max = 4000)
    private String transcripcion;
    private Integer vocesDetectadas;
    private Double confianzaVoz;
    @Size(max = 7000000)
    private String urlAudio;
    
    //Campos de una alerta de tipo proceso
    private Integer pidProceso;
    @Size(max = 500)
    private String nombreProceso;
    private CategoriaProceso categoriaProceso;
    private AccionTomada accionTomada;
    
    //Explicación en texto de la alerta (opcional, de cualquier clase): qué ocurrió y por qué se avisa. Se recorta al guardar.
    @Size(max = 20000)
    private String detalle;

    //Campos de una alerta de tipo teclado
    @Size(max = 500)
    private String combinacionTeclas;
    @Size(max = 500)
    private String patronSospechoso;

    // Identificador opcional del cliente para evitar duplicados ante reintentos de red
    @Size(max = 100)
    private String idCliente;
    
    //Constructor sin parametros
    public NuevaAlertaRequest(){
        
    }
}
