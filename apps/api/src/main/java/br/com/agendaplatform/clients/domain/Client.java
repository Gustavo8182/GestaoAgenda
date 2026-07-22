package br.com.agendaplatform.clients.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "phone_normalized", nullable = false)
    private String phoneNormalized;

    @Column(name = "alternate_phone")
    private String alternatePhone;

    @Column(name = "alternate_phone_normalized")
    private String alternatePhoneNormalized;

    @Column(name = "origin")
    private String origin;

    @Column(name = "notes")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Client() {
    }

    public Client(
            UUID organizationId,
            String name,
            String phone,
            String alternatePhone,
            String origin,
            String notes) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.name = name;
        this.phone = phone;
        this.phoneNormalized = requireValidPhone(phone, "Telefone inválido.");

        if (alternatePhone != null && !alternatePhone.isBlank()) {
            this.alternatePhone = alternatePhone;
            this.alternatePhoneNormalized = requireValidPhone(alternatePhone, "Telefone alternativo inválido.");
        }

        this.origin = blankToNull(origin);
        this.notes = blankToNull(notes);
    }

    private static String requireValidPhone(String rawPhone, String errorMessage) {
        String normalized = PhoneNormalizer.normalize(rawPhone);
        if (normalized.length() < 10 || normalized.length() > 11) {
            throw new InvalidPhoneException(errorMessage);
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getPhoneNormalized() {
        return phoneNormalized;
    }

    public String getAlternatePhone() {
        return alternatePhone;
    }

    public String getAlternatePhoneNormalized() {
        return alternatePhoneNormalized;
    }

    public String getOrigin() {
        return origin;
    }

    public String getNotes() {
        return notes;
    }
}
