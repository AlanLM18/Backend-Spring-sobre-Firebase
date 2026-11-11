package com.safemotion.backend.service;

/** Usuario autenticado de la petición: UID de Firebase Authentication + perfil de usuarios/{uid}. */
public record Sesion(String uid, String nombre, String rol) { }
