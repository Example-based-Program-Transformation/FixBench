package org.fiteagle.core.aaa;

import java.io.IOException;
import java.lang.reflect.Array;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertPathValidatorResult;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;

import org.fiteagle.core.config.FiteaglePreferences;
import org.fiteagle.core.config.FiteaglePreferencesXML;
import org.fiteagle.core.userdatabase.User;
import org.fiteagle.core.userdatabase.UserDBManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthenticationHandler {
  
  private KeyStoreManagement keyStoreManagement;
  Logger log = LoggerFactory.getLogger(this.getClass());
  
  
  public AuthenticationHandler() {
    
    this.keyStoreManagement = new KeyStoreManagement();
    
  }
  
  public void authenticateCertificates(X509Certificate[] certificates) throws KeyStoreException,
      NoSuchAlgorithmException, CertificateException, IOException, InvalidAlgorithmParameterException,
      CertPathValidatorException, SQLException {
    
    if (certificates.length == 1) {
      X509Certificate cert = certificates[0];
      if (cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal())) {
        // self signed cert
        UserDBManager userDBManager = new UserDBManager();
        User identifiedUser = userDBManager.getUserFromCert(cert);
        verifyUserSignedCertificate(identifiedUser, cert);
        signAndStoreCertificate(identifiedUser);
        
      } else {
        X500Principal issuerX500Principal = cert.getIssuerX500Principal();
        PublicKey issuerPublicKey = getTrustedIssuerPublicKey(issuerX500Principal);
        
        verifyCertificateWithPublicKey(cert, issuerPublicKey);
      }
      
    } else {
      // TODO verifyCertificateChain
    }
    
  }
  
  private PublicKey getTrustedIssuerPublicKey(X500Principal issuerX500Principal) throws KeyStoreException,
      NoSuchAlgorithmException, CertificateException, IOException {
    List<X509Certificate> storedCerts = keyStoreManagement.getTrustedCerts();
    for (X509Certificate cert : storedCerts) {
      if (!isUserCertificate(cert)) {
        if (cert.getSubjectX500Principal().equals(issuerX500Principal)) {
          return cert.getPublicKey();
        }
      }
    }
    throw new CertificateNotTrustedException();
  }
  
  private void signAndStoreCertificate(User identifiedUser) {
    CertificateAuthority ca = new CertificateAuthority();
    X509Certificate saveCert;
    try {
      saveCert = ca.createCertificate(identifiedUser);
      keyStoreManagement.storeCertificate(identifiedUser.getUID(), saveCert);
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  
  private void verifyUserSignedCertificate(User identifiedUser, X509Certificate certificate) {
    boolean verified = false;
    for (String pubKeyString : identifiedUser.getPublicKeys()) {
      KeyDecoder keydecoder = new KeyDecoder();
      PublicKey pubKey = null;
      try {
        pubKey = keydecoder.decodePublicKey(pubKeyString);
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      verified = verifyCertificateWithPublicKey(certificate, pubKey);
      if (verified) {
        return;
      }
    }
    throw new KeyDoesNotMatchException();
  }
  
  // method made public for unittesting
  public boolean verifyCertificateWithPublicKey(X509Certificate certificate, PublicKey pubKey) {
    
    try {
      
      certificate.verify(pubKey);
      return true;
    } catch (Exception e) {
      throw new KeyDoesNotMatchException();
    }
    
  }
  
  private boolean isUserCertificate(X509Certificate x509Certificate) {
    
    if (x509Certificate.getBasicConstraints() == -1) {
      return true;
    } else {
      return false;
    }
  }
  
  public class KeyDoesNotMatchException extends RuntimeException {
    
    private static final long serialVersionUID = -6825126254061827637L;
    
  }
  
  public class CertificateNotTrustedException extends RuntimeException {
    
    private static final long serialVersionUID = 6120670655966336971L;
    
  }
  
}
