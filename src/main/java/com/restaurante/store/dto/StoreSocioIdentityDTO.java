package com.restaurante.store.dto;

public class StoreSocioIdentityDTO {
    private String socioId;
    private String name;
    private String phone;
    private String email;
    private boolean socio;

    public StoreSocioIdentityDTO() {}

    public StoreSocioIdentityDTO(String socioId, String name, String phone, String email) {
        this.socioId = socioId;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.socio = true;
    }

    public static StoreSocioIdentityDTO publicBuyer(String name, String phone, String email) {
        StoreSocioIdentityDTO dto = new StoreSocioIdentityDTO();
        dto.setSocioId("PUBLIC:" + phone);
        dto.setName(name);
        dto.setPhone(phone);
        dto.setEmail(email);
        dto.setSocio(false);
        return dto;
    }

    public String getSocioId() { return socioId; }
    public void setSocioId(String socioId) { this.socioId = socioId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isSocio() { return socio; }
    public void setSocio(boolean socio) { this.socio = socio; }
}
