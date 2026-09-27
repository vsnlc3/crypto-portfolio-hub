package com.cryptoportfoliohub.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.cryptoportfoliohub.connection.credential.EncryptedCredential;

@Entity
@Table(name = "connection_credentials", uniqueConstraints =
        @UniqueConstraint(name = "uq_connection_credentials_type",
                columnNames = {"connection_id", "user_id", "credential_type"}))
public class ConnectionCredential extends UpdatedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "connection_id", referencedColumnName = "id", nullable = false),
            @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false)
    })
    private ConnectionEntity connection;

    @Column(name = "credential_type", nullable = false, length = 50)
    private String credentialType;

    @Column(name = "ciphertext", nullable = false, columnDefinition = "bytea")
    private byte[] ciphertext;

    @Column(name = "nonce", nullable = false, columnDefinition = "bytea")
    private byte[] nonce;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    protected ConnectionCredential() {
    }

    public ConnectionCredential(
            ConnectionEntity connection,
            String credentialType,
            EncryptedCredential encryptedCredential) {
        this.connection = connection;
        this.credentialType = credentialType;
        this.ciphertext = encryptedCredential.ciphertext();
        this.nonce = encryptedCredential.nonce();
        this.keyVersion = encryptedCredential.keyVersion();
    }

    public ConnectionEntity getConnection() {
        return connection;
    }

    public String getCredentialType() {
        return credentialType;
    }

    public int getKeyVersion() {
        return keyVersion;
    }

    public EncryptedCredential encryptedValue() {
        return new EncryptedCredential(ciphertext, nonce, keyVersion);
    }
}
