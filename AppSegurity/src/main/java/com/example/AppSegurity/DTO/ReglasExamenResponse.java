package com.example.AppSegurity.DTO;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReglasExamenResponse {
    private List<String> procesos_permitidos;
    private List<String> urls_permitidas;
    private String sensibilidad_ia;
    private Integer duracion_examen;
    private Integer reintentos_conexion;

    public ReglasExamenResponse(List<String> procesos, List<String> urls, String sensibilidad, Integer duracion) {
        this(procesos, urls, sensibilidad, duracion, 3);
    }

    public ReglasExamenResponse(List<String> procesos, List<String> urls, String sensibilidad, Integer duracion, Integer reintentosConexion) {
        this.procesos_permitidos = procesos;
        this.urls_permitidas = urls;
        this.sensibilidad_ia = sensibilidad;
        this.duracion_examen = duracion;
        this.reintentos_conexion = reintentosConexion;
    }
}