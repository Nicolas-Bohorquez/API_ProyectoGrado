package com.example.AppSegurity.Models;

import com.example.AppSegurity.Enums.EstadoExamen;
import com.example.AppSegurity.Sub_Clases.AnalisisGlobal_IA;
import com.example.AppSegurity.Sub_Clases.ConfiguracionExamen;
import com.example.AppSegurity.Sub_Clases.ControlAcceso;
import com.example.AppSegurity.Sub_Clases.FechaExamen;
import java.util.ArrayList;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter

@Document(collection = "examen")
@CompoundIndexes({
    @CompoundIndex(name = "idx_examen_pin_unico", def = "{'controlAcceso.pinSesion': 1}", unique = true, sparse = true),
    @CompoundIndex(name = "idx_examen_estado_fin", def = "{'controlAcceso.estadoPin': 1, 'fechaExamen.horaFin': 1}")
})
public class Examen {
    //Parametros de la clase
    //Id principales del examen
    @Id
    private String codigoExamen;
    @Indexed
    private String profesorId;
    private String materiaCodigo;
    private ControlAcceso controlAcceso;
    private FechaExamen fechaExamen;
    private ConfiguracionExamen configuracionExamen;
    private AnalisisGlobal_IA analisisGlobalIA;
    
    //Constructor sin parametros
    public Examen(){
        
    }
    
    //Constructor con parametros

    public Examen(String codigoExamen, String profesorId, String materiaCodigo, ControlAcceso controlAcceso, FechaExamen fechaExamen, ConfiguracionExamen configuracionExamen) {
        this.codigoExamen = codigoExamen;
        this.profesorId = profesorId;
        this.materiaCodigo = materiaCodigo;
        this.controlAcceso = controlAcceso;
        this.fechaExamen = fechaExamen;
        this.configuracionExamen = configuracionExamen;
    }
    
    
}
