package com.safemotion.backend.whitebox;

import com.safemotion.backend.service.AuthService;
import com.safemotion.backend.service.Reglas;
import com.safemotion.backend.service.Sesion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Práctica 11 · White Box del backend: reglas de negocio puras (Reglas.java).
 *  alertas(): D1 fc != null && fc > 110 · D2 (else) fc != null && fc < 45 · D3 temp != null && temp > 37.8
 *             · D4 spo2 != null && spo2 < 92  -> V(G) = 9 contando condiciones simples.
 *  puedeVerChat(): esClinico(rol) || (rol == paciente && uid == pacienteId) -> 4 condiciones.
 */
class ReglasWhiteBoxTest {

    @ParameterizedTest(name = "[{index}] FC={0} temp={1} SpO2={2} -> {3}")
    @DisplayName("alertas(): valores límite y todos los caminos")
    @CsvSource(nullValues = "null", value = {
            "110, 37.8, 92, ''",                                   // todas las decisiones en F, justo en el límite
            "111, 36.5, 97, Frecuencia cardiaca alta",            // D1 = V
            "45,  36.5, 97, ''",                                   // límite inferior aceptado
            "44,  36.5, 97, Frecuencia cardiaca baja",            // D1 = F, D2 = V
            "80,  37.9, 97, Temperatura elevada",                 // D3 = V
            "80,  36.5, 91, Saturación de oxígeno baja",          // D4 = V
            "null, null, null, ''",                                // condiciones c1, c3, c5 = F (valores ausentes)
            "80, 36.5, null, ''"                                   // SpO2 ausente: nunca alerta falsa
    })
    void alertas(Integer fc, Double temp, Integer spo2, String esperada) {
        List<String> a = Reglas.alertas(fc, temp, spo2);
        if (esperada.isEmpty()) assertTrue(a.isEmpty(), a.toString());
        else assertEquals(List.of(esperada), a);
    }

    @Test
    @DisplayName("alertas(): combinación de tres alertas y FC alta/baja excluyentes")
    void tresAlertas() {
        assertEquals(List.of("Frecuencia cardiaca alta", "Temperatura elevada", "Saturación de oxígeno baja"), Reglas.alertas(130, 38.5, 88));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 501, Integer.MIN_VALUE})
    @DisplayName("limiteValido(): fuera de 1..500")
    void limiteInvalido(int l) {
        assertFalse(Reglas.limiteValido(l));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 20, 500})
    @DisplayName("limiteValido(): dentro de 1..500 (incluye los límites)")
    void limiteValido(int l) {
        assertTrue(Reglas.limiteValido(l));
    }

    @Test
    @DisplayName("Roles: cobertura de condiciones de esClinico / esPersonal")
    void roles() {
        assertTrue(Reglas.esClinico("doctor"));
        assertTrue(Reglas.esClinico("admin"));
        assertFalse(Reglas.esClinico("recepcionista"));
        assertTrue(Reglas.esPersonal("recepcionista"));
        assertFalse(Reglas.esPersonal("usuario"));
        assertFalse(Reglas.esPersonal(null));
    }

    @Test
    @DisplayName("puedeVerChat(): doctor, admin, paciente propio, paciente ajeno, recepción")
    void puedeVerChat() {
        assertTrue(Reglas.puedeVerChat(new Sesion("d", "Doc", "doctor"), "p1"));
        assertTrue(Reglas.puedeVerChat(new Sesion("a", "Adm", "admin"), "p1"));
        assertTrue(Reglas.puedeVerChat(new Sesion("p1", "Juan", "usuario"), "p1"));
        assertFalse(Reglas.puedeVerChat(new Sesion("p2", "María", "usuario"), "p1"));
        assertFalse(Reglas.puedeVerChat(new Sesion("p1", "Rec", "recepcionista"), "p1"));   // aunque coincida el uid
    }

    @Test
    @DisplayName("Estados activos del SOS")
    void activa() {
        assertTrue(Reglas.activa("ABIERTA"));
        assertTrue(Reglas.activa("CANALIZADA"));
        assertFalse(Reglas.activa("ATENDIDA"));
        assertFalse(Reglas.activa(null));
    }

    @Test
    @DisplayName("Mensajes: SOS con y sin signos, canalizada y atendida")
    void mensajes() {
        assertEquals("SOS: solicito ayuda de emergencia. FC 130 bpm. SpO2 89 %. Temp. 38.2 °C.", Reglas.mensajeSos(130, 89, 38.2));
        assertEquals("SOS: solicito ayuda de emergencia.", Reglas.mensajeSos(null, null, null));
        assertTrue(Reglas.mensajeCanalizada("Dra. Ana").contains("Dra. Ana"));
        assertTrue(Reglas.mensajeAtendida("Dra. Ana").startsWith("Tu solicitud de ayuda fue atendida por Dra. Ana"));
    }

    @Test
    @DisplayName("PIN -> cuenta de Firebase: mismo esquema que src/models/user.js de la PWA")
    void credencialesDePin() {
        assertEquals("0000@pin.safemotion.app", AuthService.correoDePin("0000"));
        assertEquals("SafeMotion-0000", AuthService.passwordDePin("0000"));
    }
}
