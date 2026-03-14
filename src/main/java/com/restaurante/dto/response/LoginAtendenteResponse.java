package com.restaurante.dto.response;

import com.restaurante.model.enums.TipoUsuario;

/**
 * Response do login de Atendente/Gerente
 */
public class LoginAtendenteResponse {

    private Long id;
    private String nome;
    private String telefone;
    private String email;
    private TipoUsuario tipoUsuario;
    private String token;
    private Long expiresIn;

    public LoginAtendenteResponse() {}

    public LoginAtendenteResponse(Long id, String nome, String telefone, String email,
                                  TipoUsuario tipoUsuario, String token, Long expiresIn) {
        this.id = id;
        this.nome = nome;
        this.telefone = telefone;
        this.email = email;
        this.tipoUsuario = tipoUsuario;
        this.token = token;
        this.expiresIn = expiresIn;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public TipoUsuario getTipoUsuario() { return tipoUsuario; }
    public void setTipoUsuario(TipoUsuario tipoUsuario) { this.tipoUsuario = tipoUsuario; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(Long expiresIn) { this.expiresIn = expiresIn; }

    public static LoginAtendenteResponseBuilder builder() { return new LoginAtendenteResponseBuilder(); }

    public static class LoginAtendenteResponseBuilder {
        private Long id;
        private String nome;
        private String telefone;
        private String email;
        private TipoUsuario tipoUsuario;
        private String token;
        private Long expiresIn;

        public LoginAtendenteResponseBuilder id(Long id) { this.id = id; return this; }
        public LoginAtendenteResponseBuilder nome(String nome) { this.nome = nome; return this; }
        public LoginAtendenteResponseBuilder telefone(String telefone) { this.telefone = telefone; return this; }
        public LoginAtendenteResponseBuilder email(String email) { this.email = email; return this; }
        public LoginAtendenteResponseBuilder tipoUsuario(TipoUsuario tipoUsuario) { this.tipoUsuario = tipoUsuario; return this; }
        public LoginAtendenteResponseBuilder token(String token) { this.token = token; return this; }
        public LoginAtendenteResponseBuilder expiresIn(Long expiresIn) { this.expiresIn = expiresIn; return this; }

        public LoginAtendenteResponse build() {
            return new LoginAtendenteResponse(id, nome, telefone, email, tipoUsuario, token, expiresIn);
        }
    }
}
