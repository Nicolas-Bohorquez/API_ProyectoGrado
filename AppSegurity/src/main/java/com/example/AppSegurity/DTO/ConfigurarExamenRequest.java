package com.example.AppSegurity.DTO;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Cuerpo de PUT /api/examenes/{codigo}/configurar. Es una actualización PARCIAL: el panel
 * docente lo usa tanto al crear un examen (sin fechas) como al editarlo (con fechas), así
 * que todos los campos son opcionales y solo se envían al mapa de ExamenService los que
 * de verdad llegaron en la petición — ExamenService.configurarDesdeMapa sigue intacto.
 * Los tipos fuertes (Boolean/Integer/List) evitan el ClassCastException que antes se colaba
 * hasta el cliente cuando un campo llegaba con un tipo distinto al esperado.
 */
@Getter
@Setter
public class ConfigurarExamenRequest {

    @Size(max = 40, message = "El campo fechaString no es válido.")
    private String fechaString;

    @Size(max = 40, message = "El campo fechaFinString no es válido.")
    private String fechaFinString;

    @Size(max = 40, message = "El campo horaInicioString no es válido.")
    private String horaInicioString;

    private Boolean activarReconocimientoFacial;
    private Boolean activarDeteccionObjetos;
    private Boolean activarAnalisisAudio;
    private Boolean activarMonitoreoProcesos;
    private Boolean activarAnalisisTeclado;

    @Pattern(regexp = "BAJA|MEDIA|ALTA", message = "El campo sensibilidadIA debe ser BAJA, MEDIA o ALTA.")
    private String sensibilidadIA;

    @Min(value = 1, message = "El campo duracionExamen debe ser de al menos 1 minuto.")
    @Max(value = 1440, message = "El campo duracionExamen no puede superar 1440 minutos (24 horas).")
    private Integer duracionExamen;

    @Min(value = 0, message = "El campo permitirReintentos no puede ser negativo.")
    @Max(value = 10, message = "El campo permitirReintentos no puede superar 10.")
    private Integer permitirReintentos;

    @Size(max = 500, message = "La lista de procesos permitidos no puede superar los 500 elementos.")
    private List<@Size(max = 300, message = "Cada proceso permitido no puede superar los 300 caracteres.") String> procesosPermitidos;

    @Size(max = 500, message = "La lista de urls permitidas no puede superar los 500 elementos.")
    private List<@Size(max = 300, message = "Cada url permitida no puede superar los 300 caracteres.") String> urlsPermitidas;

    /** Solo incluye las claves presentes, igual que el Map<String,Object> que recibía el controller antes. */
    public Map<String, Object> toMap() {
        Map<String, Object> datos = new LinkedHashMap<>();
        if (fechaString != null) datos.put("fechaString", fechaString);
        if (fechaFinString != null) datos.put("fechaFinString", fechaFinString);
        if (horaInicioString != null) datos.put("horaInicioString", horaInicioString);
        if (activarReconocimientoFacial != null) datos.put("activarReconocimientoFacial", activarReconocimientoFacial);
        if (activarDeteccionObjetos != null) datos.put("activarDeteccionObjetos", activarDeteccionObjetos);
        if (activarAnalisisAudio != null) datos.put("activarAnalisisAudio", activarAnalisisAudio);
        if (activarMonitoreoProcesos != null) datos.put("activarMonitoreoProcesos", activarMonitoreoProcesos);
        if (activarAnalisisTeclado != null) datos.put("activarAnalisisTeclado", activarAnalisisTeclado);
        if (sensibilidadIA != null) datos.put("sensibilidadIA", sensibilidadIA);
        if (duracionExamen != null) datos.put("duracionExamen", duracionExamen);
        if (permitirReintentos != null) datos.put("permitirReintentos", permitirReintentos);
        if (procesosPermitidos != null) datos.put("procesosPermitidos", procesosPermitidos);
        if (urlsPermitidas != null) datos.put("urlsPermitidas", urlsPermitidas);
        return datos;
    }
}
