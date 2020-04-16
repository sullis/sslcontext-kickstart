package nl.altindag.sslcontext;

import ch.qos.logback.classic.Level;
import nl.altindag.log.LogCaptor;
import nl.altindag.sslcontext.exception.GenericKeyStoreException;
import nl.altindag.sslcontext.exception.GenericSSLContextException;
import nl.altindag.sslcontext.exception.GenericSecurityException;
import nl.altindag.sslcontext.trustmanager.CompositeX509ExtendedTrustManager;
import nl.altindag.sslcontext.util.KeyManagerUtils;
import nl.altindag.sslcontext.util.KeyStoreUtils;
import nl.altindag.sslcontext.util.TrustManagerUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static nl.altindag.sslcontext.TestConstants.IDENTITY_FILE_NAME;
import static nl.altindag.sslcontext.TestConstants.IDENTITY_PASSWORD;
import static nl.altindag.sslcontext.TestConstants.KEYSTORE_LOCATION;
import static nl.altindag.sslcontext.TestConstants.TEMPORALLY_KEYSTORE_LOCATION;
import static nl.altindag.sslcontext.TestConstants.TRUSTSTORE_FILE_NAME;
import static nl.altindag.sslcontext.TestConstants.TRUSTSTORE_PASSWORD;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

public class SSLFactoryShould {

    private static final Logger LOGGER = LoggerFactory.getLogger(SSLFactoryShould.class);

    private static final String GENERIC_IDENTITY_VALIDATION_EXCEPTION_MESSAGE = "Identity details are empty, which are required to be present when SSL/TLS is enabled";
    private static final String GENERIC_TRUSTSTORE_VALIDATION_EXCEPTION_MESSAGE = "TrustStore details are empty, which are required to be present when SSL/TLS is enabled";
    private static final String GENERIC_TRUST_STRATEGY_VALIDATION_EXCEPTION_MESSAGE = "Trust strategy is missing. Please validate if the TrustStore is present, " +
            "or including default JDK TrustStore is enabled, " +
            "or TrustManager is present, " +
            "or trusting all certificates without validation is enabled";

    @Test
    public void buildSSLFactoryWithTrustMaterial() {
        SSLFactory sslFactory = SSLFactory.builder()
                .withTrustStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, TRUSTSTORE_PASSWORD)
                .build();

        assertThat(sslFactory.getSslContext()).isNotNull();

        assertThat(sslFactory.getTrustManager()).isNotNull();
        assertThat(sslFactory.getTrustedCertificates()).isNotEmpty();
        assertThat(sslFactory.getTrustStores()).isNotEmpty();
        assertThat(sslFactory.getHostnameVerifier()).isNotNull();
        assertThat(sslFactory.getKeyManager()).isNotPresent();
        assertThat(sslFactory.getSslContext().getProtocol()).isEqualTo("TLSv1.2");
    }

    @Test
    public void buildSSLFactoryWithTrustMaterialFromPath() throws IOException {
        Path trustStorePath = copyKeystoreToHomeDirectory(KEYSTORE_LOCATION, TRUSTSTORE_FILE_NAME);

        SSLFactory sslFactory = SSLFactory.builder()
                .withTrustStore(trustStorePath, TRUSTSTORE_PASSWORD)
                .build();

        assertThat(sslFactory.getSslContext()).isNotNull();

        assertThat(sslFactory.getTrustManager()).isNotNull();
        assertThat(sslFactory.getTrustedCertificates()).isNotEmpty();
        assertThat(sslFactory.getTrustStores()).isNotEmpty();
        assertThat(sslFactory.getHostnameVerifier()).isNotNull();

        assertThat(sslFactory.getKeyManager()).isNotPresent();
        assertThat(sslFactory.getIdentities()).isEmpty();
        assertThat(sslFactory.getSslContext().getProtocol()).isEqualTo("TLSv1.2");

        Files.delete(trustStorePath);
    }

    @Test
    public void buildSSLFactoryWithTrustMaterialFromKeyStore() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        KeyStore trustStore = KeyStoreUtils.loadKeyStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, TRUSTSTORE_PASSWORD);

        SSLFactory sslFactory = SSLFactory.builder()
                .withTrustStore(trustStore, TRUSTSTORE_PASSWORD)
                .build();

        assertThat(sslFactory.getSslContext()).isNotNull();

        assertThat(sslFactory.getTrustManager()).isNotNull();
        assertThat(sslFactory.getTrustedCertificates()).isNotEmpty();
        assertThat(sslFactory.getTrustStores()).isNotEmpty();
        assertThat(sslFactory.getHostnameVerifier()).isNotNull();

        assertThat(sslFactory.getKeyManager()).isNotPresent();
        assertThat(sslFactory.getIdentities()).isEmpty();
        assertThat(sslFactory.getSslContext().getProtocol()).isEqualTo("TLSv1.2");
    }

    @Test
    public void buildSSLFactoryWithTrustMaterialFromTrustManager() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        KeyStore trustStore = KeyStoreUtils.loadKeyStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, TRUSTSTORE_PASSWORD);
        X509TrustManager trustManager = TrustManagerUtils.createTrustManager(trustStore);

        SSLFactory sslFactory = SSLFactory.builder()
                .withTrustManager(trustManager)
                .build();

        assertThat(sslFactory.getSslContext()).isNotNull();

        assertThat(sslFactory.getTrustManager()).isNotNull();
        assertThat(sslFactory.getTrustedCertificates()).isNotEmpty();
        assertThat(sslFactory.getTrustStores()).isEmpty();
        assertThat(sslFactory.getHostnameVerifier()).isNotNull();

        assertThat(sslFactory.getKeyManager()).isNotPresent();
        assertThat(sslFactory.getIdentities()).isEmpty();
        assertThat(sslFactory.getSslContext().getProtocol()).isEqualTo("TLSv1.2");
    }

    @Test
    public void buildSSLFactoryWithTrustMaterialFromOnlyJdkTrustedCertificates() {
        SSLFactory sslFactory = SSLFactory.builder()
                .withDefaultJdkTrustStore()
                .build();

        assertThat(sslFactory.getSslContext()).isNotNull();

        assertThat(sslFactory.getTrustManager()).isNotNull();
        assertThat(sslFactory.getTrustStores()).isEmpty();
        assertThat(sslFactory.getTrustedCertificates()).hasSizeGreaterThan(10);

        assertThat(sslFactory.getKeyManager()).isNotPresent();
        assertThat(sslFactory.getIdentities()).isEmpty();
        assertThat(sslFactory.getSslContext().getProtocol()).isEqualTo("TLSv1.2");
    }

    @Test
    public void buildSSLFactoryWithSecureRandom() throws NoSuchAlgorithmException {
        SSLFactory sslFactory = SSLFactory.builder()
                .withSecureRandom(SecureRandom.getInstanceStrong())
                .withDefaultJdkTrustStore()
                .build();

        assertThat(sslFactory.getSslContext()).isNotNull();

        assertThat(sslFactory.getTrustManager()).isNotNull();
        assertThat(sslFactory.getTrustStores()).isEmpty();
        assertThat(sslFactory.getTrustedCertificates()).hasSizeGreaterThan(10);

        assertThat(sslFactory.getKeyManager()).isNotPresent();
        assertThat(sslFactory.getIdentities()).isEmpty();
        assertThat(sslFactory.getSslContext().getProtocol()).isEqualTo("TLSv1.2");
    }

    @Test
    public void buildSSLFactoryWithTrustMaterialFromJdkTrustedCertificatesAndCustomTrustStore() {
        SSLFactory sslFactory = SSLFactory.builder()
                .withTrustStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, TRUSTSTORE_PASSWORD)
                .withDefaultJdkTrustStore()
                .build();

        assertThat(sslFactory.getTrustManager()).isNotNull();
        assertThat(sslFactory.getTrustStores()).isNotEmpty();
        assertThat(sslFactory.getTrustStores().get(0).getKeyStorePassword()).isEqualTo(TRUSTSTORE_PASSWORD);
        assertThat(sslFactory.getTrustedCertificates()).hasSizeGreaterThan(10);
        assertThat(Arrays.stream(sslFactory.getTrustedCertificates())
                .map(X509Certificate::getSubjectX500Principal)
                .map(X500Principal::toString)).contains("CN=*.google.com, O=Google LLC, L=Mountain View, ST=California, C=US");

        assertThat(sslFactory.getKeyManager()).isNotPresent();
        assertThat(sslFactory.getIdentities()).isEmpty();
        assertThat(sslFactory.getSslContext().getProtocol()).isEqualTo("TLSv1.2");
    }

    @Test
    public void buildSSLFactoryWithIdentityMaterialAndTrustMaterial() {
        SSLFactory sslFactory = SSLFactory.builder()
                .withIdentity(KEYSTORE_LOCATION + IDENTITY_FILE_NAME, IDENTITY_PASSWORD)
                .withTrustStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, TRUSTSTORE_PASSWORD)
                .build();

        assertThat(sslFactory.getSslContext()).isNotNull();

        assertThat(sslFactory.getKeyManager()).isPresent();
        assertThat(sslFactory.getIdentities()).isNotEmpty();
        assertThat(sslFactory.getIdentities().get(0).getKeyStorePassword()).isEqualTo(IDENTITY_PASSWORD);

        assertThat(sslFactory.getTrustManager()).isNotNull();
        assertThat(sslFactory.getTrustedCertificates()).isNotEmpty();
        assertThat(sslFactory.getTrustStores()).isNotEmpty();
        assertThat(sslFactory.getTrustStores().get(0).getKeyStorePassword()).isEqualTo(TRUSTSTORE_PASSWORD);
        assertThat(sslFactory.getTrustManager()).isNotNull();
        assertThat(sslFactory.getHostnameVerifier()).isNotNull();
        assertThat(sslFactory.getSslContext().getProtocol()).isEqualTo("TLSv1.2");
    }

    @Test
    public void buildSSLFactoryWithIdentityMaterialAndTrustMaterialFromIdentityManagerAndTrustStore() throws Exception {
        KeyStore identity = KeyStoreUtils.loadKeyStore(KEYSTORE_LOCATION + IDENTITY_FILE_NAME, IDENTITY_PASSWORD);
        X509KeyManager identityManager = KeyManagerUtils.createKeyManager(identity, IDENTITY_PASSWORD);

        SSLFactory sslFactory = SSLFactory.builder()
                .withKeyManager(identityManager)
                .withTrustStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, TRUSTSTORE_PASSWORD)
                .build();

        assertThat(sslFactory.getSslContext()).isNotNull();

        assertThat(sslFactory.getKeyManager()).isPresent();
        assertThat(sslFactory.getIdentities()).isEmpty();

        assertThat(sslFactory.getTrustManager()).isNotNull();
        assertThat(sslFactory.getTrustedCertificates()).isNotEmpty();
        assertThat(sslFactory.getTrustStores()).isNotEmpty();
        assertThat(sslFactory.getTrustStores().get(0).getKeyStorePassword()).isEqualTo(TRUSTSTORE_PASSWORD);
        assertThat(sslFactory.getTrustManager()).isNotNull();
        assertThat(sslFactory.getHostnameVerifier()).isNotNull();
        assertThat(sslFactory.getSslContext().getProtocol()).isEqualTo("TLSv1.2");
    }

    @Test
    public void buildSSLFactoryWithIdentityMaterialAndTrustMaterialFromKeyStoreAndOnlyJdkTrustedCertificates() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        KeyStore identity = KeyStoreUtils.loadKeyStore(KEYSTORE_LOCATION + IDENTITY_FILE_NAME, IDENTITY_PASSWORD);
        SSLFactory sslFactory = SSLFactory.builder()
                .withIdentity(identity, IDENTITY_PASSWORD)
                .withDefaultJdkTrustStore()
                .build();

        assertThat(sslFactory.getSslContext()).isNotNull();

        assertThat(sslFactory.getKeyManager()).isPresent();
        assertThat(sslFactory.getIdentities()).isNotEmpty();
        assertThat(sslFactory.getIdentities().get(0).getKeyStorePassword()).isEqualTo(IDENTITY_PASSWORD);

        assertThat(sslFactory.getTrustManager()).isNotNull();
        assertThat(sslFactory.getTrustedCertificates()).isNotEmpty();
        assertThat(sslFactory.getTrustStores()).isEmpty();
        assertThat(sslFactory.getTrustManager()).isNotNull();
        assertThat(sslFactory.getHostnameVerifier()).isNotNull();
        assertThat(sslFactory.getSslContext().getProtocol()).isEqualTo("TLSv1.2");
    }

    @Test
    public void buildSSLFactoryWithIdentityMaterialAndTrustMaterialFromKeyStoreAndTrustStoreWithPath() throws IOException {
        Path identityPath = copyKeystoreToHomeDirectory(KEYSTORE_LOCATION, IDENTITY_FILE_NAME);
        Path trustStorePath = copyKeystoreToHomeDirectory(KEYSTORE_LOCATION, TRUSTSTORE_FILE_NAME);

        SSLFactory sslFactory = SSLFactory.builder()
                .withIdentity(identityPath, IDENTITY_PASSWORD)
                .withTrustStore(trustStorePath, TRUSTSTORE_PASSWORD)
                .build();

        assertThat(sslFactory.getSslContext()).isNotNull();

        assertThat(sslFactory.getKeyManager()).isPresent();
        assertThat(sslFactory.getIdentities()).isNotEmpty();
        assertThat(sslFactory.getIdentities().get(0).getKeyStorePassword()).isEqualTo(IDENTITY_PASSWORD);

        assertThat(sslFactory.getTrustManager()).isNotNull();
        assertThat(sslFactory.getTrustedCertificates()).isNotEmpty();
        assertThat(sslFactory.getTrustStores()).isNotEmpty();
        assertThat(sslFactory.getTrustStores().get(0).getKeyStorePassword()).isEqualTo(TRUSTSTORE_PASSWORD);
        assertThat(sslFactory.getTrustManager()).isNotNull();
        assertThat(sslFactory.getHostnameVerifier()).isNotNull();
        assertThat(sslFactory.getSslContext().getProtocol()).isEqualTo("TLSv1.2");

        Files.delete(identityPath);
        Files.delete(trustStorePath);
    }

    @Test
    public void buildSSLFactoryWithIdentityMaterialAndTrustMaterialFromKeyStoreAndTrustStore() throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException {
        KeyStore identity = KeyStoreUtils.loadKeyStore(KEYSTORE_LOCATION + IDENTITY_FILE_NAME, IDENTITY_PASSWORD);
        KeyStore trustStore = KeyStoreUtils.loadKeyStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, TRUSTSTORE_PASSWORD);

        SSLFactory sslFactory = SSLFactory.builder()
                .withIdentity(identity, IDENTITY_PASSWORD)
                .withTrustStore(trustStore, TRUSTSTORE_PASSWORD)
                .build();

        assertThat(sslFactory.getSslContext()).isNotNull();

        assertThat(sslFactory.getKeyManager()).isPresent();
        assertThat(sslFactory.getIdentities()).isNotEmpty();
        assertThat(sslFactory.getIdentities().get(0).getKeyStorePassword()).isEqualTo(IDENTITY_PASSWORD);

        assertThat(sslFactory.getTrustManager()).isNotNull();
        assertThat(sslFactory.getTrustedCertificates()).isNotEmpty();
        assertThat(sslFactory.getTrustStores()).isNotEmpty();
        assertThat(sslFactory.getTrustStores().get(0).getKeyStorePassword()).isEqualTo(TRUSTSTORE_PASSWORD);
        assertThat(sslFactory.getTrustManager()).isNotNull();
        assertThat(sslFactory.getHostnameVerifier()).isNotNull();
        assertThat(sslFactory.getSslContext().getProtocol()).isEqualTo("TLSv1.2");
    }

    @Test
    public void buildSSLFactoryWithCustomHostnameVerifier() {
        SSLFactory sslFactory = SSLFactory.builder()
                .withTrustStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, TRUSTSTORE_PASSWORD)
                .withHostnameVerifier((host, sslSession) -> true)
                .build();

        HostnameVerifier hostnameVerifier = sslFactory.getHostnameVerifier();
        assertThat(hostnameVerifier.verify("qwerty", null)).isTrue();
    }

    @Test
    public void buildSSLFactoryWithoutHostnameVerifierProvidesDefaultHostnameVerifier() {
        SSLFactory sslFactory = SSLFactory.builder()
                .withTrustStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, TRUSTSTORE_PASSWORD)
                .build();

        SSLSession sslSession = new SSLSession() {
            @Override
            public byte[] getId() {
                return new byte[0];
            }

            @Override
            public SSLSessionContext getSessionContext() {
                return null;
            }

            @Override
            public long getCreationTime() {
                return 0;
            }

            @Override
            public long getLastAccessedTime() {
                return 0;
            }

            @Override
            public void invalidate() {

            }

            @Override
            public boolean isValid() {
                return false;
            }

            @Override
            public void putValue(String s, Object o) {

            }

            @Override
            public Object getValue(String s) {
                return null;
            }

            @Override
            public void removeValue(String s) {

            }

            @Override
            public String[] getValueNames() {
                return new String[0];
            }

            @Override
            public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
                return new Certificate[0];
            }

            @Override
            public Certificate[] getLocalCertificates() {
                return new Certificate[0];
            }

            @Override
            public javax.security.cert.X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
                return new javax.security.cert.X509Certificate[0];
            }

            @Override
            public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
                return null;
            }

            @Override
            public Principal getLocalPrincipal() {
                return null;
            }

            @Override
            public String getCipherSuite() {
                return null;
            }

            @Override
            public String getProtocol() {
                return null;
            }

            @Override
            public String getPeerHost() {
                return "localhost";
            }

            @Override
            public int getPeerPort() {
                return 0;
            }

            @Override
            public int getPacketBufferSize() {
                return 0;
            }

            @Override
            public int getApplicationBufferSize() {
                return 0;
            }
        };

        HostnameVerifier hostnameVerifier = sslFactory.getHostnameVerifier();
        assertThat(hostnameVerifier.verify("localhost", sslSession)).isTrue();
    }

    @Test
    public void buildSSLFactoryWithTlsProtocolVersionOneDotOne() {
        SSLFactory sslFactory = SSLFactory.builder()
                .withTrustStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, TRUSTSTORE_PASSWORD)
                .withProtocol("TLSv1.1")
                .build();

        assertThat(sslFactory.getSslContext()).isNotNull();
        assertThat(sslFactory.getSslContext().getProtocol()).isEqualTo("TLSv1.1");
    }

    @Test
    public void buildSSLFactoryWithTrustingAllCertificatesWithoutValidation() {
        LogCaptor<SSLFactory> logCaptor = LogCaptor.forClass(SSLFactory.class);

        SSLFactory sslFactory = SSLFactory.builder()
                .withTrustingAllCertificatesWithoutValidation()
                .build();

        assertThat(sslFactory.getSslContext()).isNotNull();
        assertThat(sslFactory.getTrustedCertificates()).isEmpty();
        assertThat(sslFactory.getTrustStores()).isEmpty();
        assertThat(sslFactory.getTrustManager()).isInstanceOf(CompositeX509ExtendedTrustManager.class);
        assertThat(logCaptor.getLogs(Level.WARN)).contains("UnsafeTrustManager is being used. Client/Server certificates will be accepted without validation. Please don't use this configuration at production.");
    }

    @Test
    public void throwExceptionWhenSSLFactoryIsBuildWithoutIdentityAndTrustMaterial() {
        assertThatThrownBy(() -> SSLFactory.builder().build())
                .isInstanceOf(GenericSecurityException.class)
                .hasMessage("Could not create instance of SSLFactory because Identity and Trust material are not present. Please provide at least a Trust material.");
    }

    @Test
    public void buildSSLFactoryWithTLSProtocolVersionOneDotThreeIfJavaVersionIsElevenOrGreater() {
        Pattern valueBeforeDotPattern = Pattern.compile("^([^.]+)");

        String javaVersion = System.getProperty("java.version");
        Matcher matcher = valueBeforeDotPattern.matcher(javaVersion);
        if (!matcher.find()) {
            fail("Could not find the java version");
        }

        int javaMajorVersion = Integer.parseInt(matcher.group(0));
        if (javaMajorVersion < 11) {
            LOGGER.info("skipping unit test [{}] because TLSv1.3 is not available for this java {} version",
                        new Object() {}.getClass().getEnclosingMethod().getName(),
                        javaVersion);
            return;
        }

        LOGGER.info("Found java version {}, including testing SSLFactory with TLSv1.3 protocol", javaMajorVersion);
        SSLFactory sslFactory = SSLFactory.builder()
                .withDefaultJdkTrustStore()
                .withProtocol("TLSv1.3")
                .build();

        assertThat(sslFactory.getSslContext()).isNotNull();
        assertThat(sslFactory.getSslContext().getProtocol()).isEqualTo("TLSv1.3");
    }

    @Test
    public void throwExceptionWhenBuildingSSLFactoryWithTrustStoreWhileProvidingWrongPassword() {
        assertThatThrownBy(() -> SSLFactory.builder().withTrustStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, "password".toCharArray()))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessage("Failed to load the keystore");
    }

    @Test
    public void throwExceptionWhenBuildingSSLFactoryWithTrustStoreFromPathWhileProvidingWrongPassword() throws IOException {
        Path trustStorePath = copyKeystoreToHomeDirectory(KEYSTORE_LOCATION, TRUSTSTORE_FILE_NAME);

        assertThatThrownBy(() -> SSLFactory.builder().withTrustStore(trustStorePath, "password".toCharArray()))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessage("Failed to load the keystore");

        Files.delete(trustStorePath);
    }

    @Test
    public void throwExceptionWhenBuildingSSLFactoryWithIdentityWhileProvidingWrongPassword() {
        assertThatThrownBy(() -> SSLFactory.builder().withIdentity(KEYSTORE_LOCATION + IDENTITY_FILE_NAME, "password".toCharArray()))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessage("Failed to load the keystore");
    }

    @Test
    public void throwExceptionWhenBuildingSSLFactoryWithIdentityFromPathWhileProvidingWrongPassword() throws IOException {
        Path identityPath = copyKeystoreToHomeDirectory(KEYSTORE_LOCATION, IDENTITY_FILE_NAME);

        assertThatThrownBy(() -> SSLFactory.builder().withIdentity(identityPath, "password".toCharArray()))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessage("Failed to load the keystore");

        Files.delete(identityPath);
    }

    @Test
    public void throwExceptionWhenBuildingSSLFactoryWithNullAsTrustStorePath() {
        assertThatThrownBy(() -> SSLFactory.builder().withTrustStore((Path) null, "secret".toCharArray()))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessage(GENERIC_TRUSTSTORE_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionWhenBuildingSSLFactoryWithEmptyTrustStorePassword() throws IOException {
        Path trustStorePath = copyKeystoreToHomeDirectory(KEYSTORE_LOCATION, TRUSTSTORE_FILE_NAME);

        assertThatThrownBy(() -> SSLFactory.builder().withTrustStore(trustStorePath, EMPTY.toCharArray()))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessage(GENERIC_TRUSTSTORE_VALIDATION_EXCEPTION_MESSAGE);

        Files.delete(trustStorePath);
    }

    @Test
    public void throwExceptionWhenBuildingSSLFactoryWithEmptyTrustStoreType() throws IOException {
        Path trustStorePath = copyKeystoreToHomeDirectory(KEYSTORE_LOCATION, TRUSTSTORE_FILE_NAME);

        assertThatThrownBy(() -> SSLFactory.builder().withTrustStore(trustStorePath, TRUSTSTORE_PASSWORD, EMPTY))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessage(GENERIC_TRUSTSTORE_VALIDATION_EXCEPTION_MESSAGE);

        Files.delete(trustStorePath);
    }

    @Test
    public void throwExceptionWhenBuildingSSLFactoryWithTrustStoreAsNull() {
        assertThatThrownBy(() -> SSLFactory.builder().withTrustStore((KeyStore) null, TRUSTSTORE_PASSWORD))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessage(GENERIC_TRUSTSTORE_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionWhenBuildingSSLFactoryWithEmptyTrustStorePasswordWhileUsingKeyStoreObject() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        KeyStore trustStore = KeyStoreUtils.loadKeyStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, TRUSTSTORE_PASSWORD);

        assertThatThrownBy(() -> SSLFactory.builder().withTrustStore(trustStore, EMPTY.toCharArray()))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessage(GENERIC_TRUSTSTORE_VALIDATION_EXCEPTION_MESSAGE);
    }


    @Test
    public void throwExceptionWhenKeyStoreFileIsNotFound() {
        assertThatThrownBy(() -> SSLFactory.builder().withTrustStore(KEYSTORE_LOCATION + "not-existing-truststore.jks", TRUSTSTORE_PASSWORD))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessage("Failed to load the keystore");
    }

    @Test
    public void throwExceptionWhenTrustStorePathIsNotProvided() {
        assertThatThrownBy(() -> SSLFactory.builder().withTrustStore(EMPTY, TRUSTSTORE_PASSWORD))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessage(GENERIC_TRUSTSTORE_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionWhenTrustStorePasswordIsNotProvided() {
        assertThatThrownBy(() -> SSLFactory.builder().withTrustStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, EMPTY.toCharArray()))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessage(GENERIC_TRUSTSTORE_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionWhenIdentityPathIsNotProvided() {
        assertThatThrownBy(() -> SSLFactory.builder().withIdentity(EMPTY, IDENTITY_PASSWORD))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessage(GENERIC_IDENTITY_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionWhenIdentityPasswordIsNotProvided() {
        assertThatThrownBy(() -> SSLFactory.builder().withIdentity(KEYSTORE_LOCATION + IDENTITY_FILE_NAME, EMPTY.toCharArray()))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessage(GENERIC_IDENTITY_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionWhenIdentityTypeIsNotProvided() {
        assertThatThrownBy(() -> SSLFactory.builder().withIdentity(KEYSTORE_LOCATION + IDENTITY_FILE_NAME, IDENTITY_PASSWORD, EMPTY))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessage(GENERIC_IDENTITY_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionWhenIdentityPathIsNull() {
        assertThatThrownBy(() -> SSLFactory.builder().withIdentity((Path) null, IDENTITY_PASSWORD))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessage(GENERIC_IDENTITY_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionWhenIdentityPasswordIsNotProvidedWhileUsingPath() throws IOException {
        Path identityPath = copyKeystoreToHomeDirectory(KEYSTORE_LOCATION, IDENTITY_FILE_NAME);

        assertThatThrownBy(() -> SSLFactory.builder().withIdentity(identityPath, EMPTY.toCharArray()))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessage(GENERIC_IDENTITY_VALIDATION_EXCEPTION_MESSAGE);

        Files.delete(identityPath);
    }

    @Test
    public void throwExceptionWhenIdentityIsNull() {
        assertThatThrownBy(() -> SSLFactory.builder().withIdentity((KeyStore) null, IDENTITY_PASSWORD))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessage(GENERIC_IDENTITY_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionWhenIdentityPasswordIsEmptyWhileUsingKeyStoreAsObject() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        KeyStore identity = KeyStoreUtils.loadKeyStore(KEYSTORE_LOCATION + IDENTITY_FILE_NAME, IDENTITY_PASSWORD);

        assertThatThrownBy(() -> SSLFactory.builder().withIdentity(identity, EMPTY.toCharArray()))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessage(GENERIC_IDENTITY_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionWhenIdentityTypeIsNotProvidedWhileUsingPath() throws IOException {
        Path identityPath = copyKeystoreToHomeDirectory(KEYSTORE_LOCATION, IDENTITY_FILE_NAME);

        assertThatThrownBy(() -> SSLFactory.builder().withIdentity(identityPath, IDENTITY_PASSWORD, EMPTY))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessage(GENERIC_IDENTITY_VALIDATION_EXCEPTION_MESSAGE);

        Files.delete(identityPath);
    }

    @Test
    public void throwExceptionWhenTrustMaterialIsMissingAlthoughIdentityMaterialIsPresent() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        KeyStore identity = KeyStoreUtils.loadKeyStore(KEYSTORE_LOCATION + IDENTITY_FILE_NAME, IDENTITY_PASSWORD);

        assertThatThrownBy(() -> SSLFactory.builder()
                .withIdentity(identity, IDENTITY_PASSWORD)
                .build())
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessage(GENERIC_TRUST_STRATEGY_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionWhenProvidingAnInvalidEncryptionProtocol() {
        assertThatThrownBy(() -> SSLFactory.builder()
                                           .withTrustingAllCertificatesWithoutValidation()
                                           .withProtocol("ENCRYPTIONv1.1")
                                           .build())
                .isInstanceOf(GenericSSLContextException.class)
                .hasMessage("java.security.NoSuchAlgorithmException: ENCRYPTIONv1.1 SSLContext not available");
    }

    @SuppressWarnings("SameParameterValue")
    private Path copyKeystoreToHomeDirectory(String path, String fileName) throws IOException {
        try (InputStream keystoreInputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path + fileName)) {
            Path destination = Paths.get(TEMPORALLY_KEYSTORE_LOCATION, fileName);
            Files.copy(Objects.requireNonNull(keystoreInputStream), destination, REPLACE_EXISTING);
            return destination;
        }
    }

}