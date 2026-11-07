package com.example.AppSegurity.Models;

import com.example.AppSegurity.Enums.EstadoSesion;
import com.example.AppSegurity.Sub_Clases.AnalisisGlobal_IA;
import com.example.AppSegurity.Sub_Clases.Conexion;
import com.example.AppSegurity.Sub_Clases.Proceso_apelacion;
import com.example.AppSegurity.Sub_Clases.ReporteFinal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter

@Document(collection = "sesion_examen")
public class SesionSupervision {
    //Parametros de la clase
    @Id
    private String sesionId;
    private String examenId;
    private String estudianteId;
    private EstadoSesion estadoSesion;
    private Conexion conexion;
    private AnalisisGlobal_IA analisisGlobalIA;
    private ReporteFinal reporteFinal;
    private Proceso_apelacion apelacion;

    // Clave AES (Fernet) de ESTA sesión para cifrar el video, las evidencias y la voz. En la base de datos se guarda cifrada con
    // la llave del servidor; hacia el cliente sale descifrada como "claveSesion" (solo al estudiante dueño y a su docente).
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String claveSesionCifrada;
    
    @org.springframework.data.annotation.Transient
    private String nombreEstudiante;
    
    @org.springframework.data.annotation.Transient
    private String cedula;
    
    @org.springframework.data.annotation.Transient
    private int cantidadAlertas;
    
    @org.springframework.data.annotation.Transient
    private int porcentajeIntegridad;
    
    //Constructor sin parametros
    public SesionSupervision(){
    }

    /** Clave de la sesión en claro (para entregarla por HTTPS a quien corresponde). Nunca se loguea. */
    @com.fasterxml.jackson.annotation.JsonProperty("claveSesion")
    public String getClaveSesion() {
        if (claveSesionCifrada == null || claveSesionCifrada.isBlank()) return null;
        try {
            return com.example.AppSegurity.Security.AESEncryptionUtil.desencriptarDatoSensible(claveSesionCifrada);
        } catch (RuntimeException e) {
            return null;        // llave del servidor cambiada: la sesión sigue listándose, solo sin clave (nunca un error 500)
        }
    }

    /** Genera la clave de la sesión si todavía no tiene (sesiones nuevas y antiguas). Devuelve true si la creó. */
    public boolean asegurarClaveSesion() {
        if (claveSesionCifrada != null && !claveSesionCifrada.isBlank()) return false;
        claveSesionCifrada = com.example.AppSegurity.Security.AESEncryptionUtil.encriptarDatoSensible(
                com.example.AppSegurity.Security.GeneradorClaves.claveFernet());
        return true;
    }
    
    //Constructor con parametros

    public SesionSupervision(String sesionId, String examenId, String estudianteId, EstadoSesion estadoSesion, Conexion conexion, AnalisisGlobal_IA analisisGlobalIA, ReporteFinal reporteFinal, Proceso_apelacion apelacion) {
        this.sesionId = sesionId;
        this.examenId = examenId;
        this.estudianteId = estudianteId;
        this.estadoSesion = estadoSesion;
        this.conexion = conexion;
        this.analisisGlobalIA = analisisGlobalIA;
        this.reporteFinal = reporteFinal;
        this.apelacion = apelacion;
    }
    
}
