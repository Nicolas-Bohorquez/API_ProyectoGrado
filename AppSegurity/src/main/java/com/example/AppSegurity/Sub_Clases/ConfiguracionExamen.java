package com.example.AppSegurity.Sub_Clases;

import com.example.AppSegurity.Enums.Sensibilidad_IA;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfiguracionExamen {
    //Parametros de la clase
    private Boolean activarReconocimientoFacial;
    private Boolean activarDeteccionObjetos;
    private Boolean activarAnalisisAudio;
    private Boolean activarMonitoreoProcesos;
    private Boolean activarAnalisisTeclado;
    private Sensibilidad_IA sensibilidadIA; // BAJA, MEDIA O ALTA
    private Integer duracionExamen;
    private Integer permitirReintentos;
    private Integer intentosMaximos;
    private List<String> procesosPermitidos; //Lista blanca
    private List<String> urlsPermitidas; 

    
    //Constructor sin parametros
    public ConfiguracionExamen() {
        
    }
    
    //Constructor con parametros

    public ConfiguracionExamen(Boolean activarReconocimientoFacial, Boolean activarDeteccionObjetos, Boolean activarAnalisisAudio, Boolean activarMonitoreoProcesos, Boolean activarAnalisisTeclado, Sensibilidad_IA sensibilidadIA, Integer duracionExamen, Integer permitirReintentos, List<String> procesosPermitidos, List<String> urlsPermitidas) {
        this.activarReconocimientoFacial = activarReconocimientoFacial;
        this.activarDeteccionObjetos = activarDeteccionObjetos;
        this.activarAnalisisAudio = activarAnalisisAudio;
        this.activarMonitoreoProcesos = activarMonitoreoProcesos;
        this.activarAnalisisTeclado = activarAnalisisTeclado;
        this.sensibilidadIA = sensibilidadIA;
        this.duracionExamen = duracionExamen;
        this.permitirReintentos = permitirReintentos;
        this.procesosPermitidos = procesosPermitidos;
        this.urlsPermitidas = urlsPermitidas;
    }
    
    
    
}
