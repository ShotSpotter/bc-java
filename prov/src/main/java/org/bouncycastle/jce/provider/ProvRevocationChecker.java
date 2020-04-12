package org.bouncycastle.jce.provider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.Extension;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1GeneralizedTime;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.bsi.BSIObjectIdentifiers;
import org.bouncycastle.asn1.cryptopro.CryptoProObjectIdentifiers;
import org.bouncycastle.asn1.eac.EACObjectIdentifiers;
import org.bouncycastle.asn1.isara.IsaraObjectIdentifiers;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.ocsp.BasicOCSPResponse;
import org.bouncycastle.asn1.ocsp.CertID;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.ocsp.OCSPRequest;
import org.bouncycastle.asn1.ocsp.OCSPResponse;
import org.bouncycastle.asn1.ocsp.OCSPResponseStatus;
import org.bouncycastle.asn1.ocsp.Request;
import org.bouncycastle.asn1.ocsp.ResponseBytes;
import org.bouncycastle.asn1.ocsp.ResponseData;
import org.bouncycastle.asn1.ocsp.RevokedInfo;
import org.bouncycastle.asn1.ocsp.SingleResponse;
import org.bouncycastle.asn1.ocsp.TBSRequest;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.RSASSAPSSparams;
import org.bouncycastle.asn1.rosstandart.RosstandartObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.jcajce.PKIXCertRevocationChecker;
import org.bouncycastle.jcajce.PKIXCertRevocationCheckerParameters;
import org.bouncycastle.jcajce.util.JcaJceHelper;
import org.bouncycastle.jcajce.util.MessageDigestUtils;
import org.bouncycastle.jce.exception.ExtCertPathValidatorException;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.Properties;
import org.bouncycastle.util.io.Streams;

class ProvRevocationChecker
    extends PKIXRevocationChecker
    implements PKIXCertRevocationChecker
{
    private static final int DEFAULT_OCSP_TIMEOUT = 15000;
    private static final int DEFAULT_OCSP_MAX_RESPONSE_SIZE = 32 * 1024;

    private static final Map oids = new HashMap();

    static
    {
        //
        // reverse mappings
        //
        oids.put(new ASN1ObjectIdentifier("1.2.840.113549.1.1.5"), "SHA1WITHRSA");
        oids.put(PKCSObjectIdentifiers.sha224WithRSAEncryption, "SHA224WITHRSA");
        oids.put(PKCSObjectIdentifiers.sha256WithRSAEncryption, "SHA256WITHRSA");
        oids.put(PKCSObjectIdentifiers.sha384WithRSAEncryption, "SHA384WITHRSA");
        oids.put(PKCSObjectIdentifiers.sha512WithRSAEncryption, "SHA512WITHRSA");
        oids.put(CryptoProObjectIdentifiers.gostR3411_94_with_gostR3410_94, "GOST3411WITHGOST3410");
        oids.put(CryptoProObjectIdentifiers.gostR3411_94_with_gostR3410_2001, "GOST3411WITHECGOST3410");
        oids.put(RosstandartObjectIdentifiers.id_tc26_signwithdigest_gost_3410_12_256, "GOST3411-2012-256WITHECGOST3410-2012-256");
        oids.put(RosstandartObjectIdentifiers.id_tc26_signwithdigest_gost_3410_12_512, "GOST3411-2012-512WITHECGOST3410-2012-512");
        oids.put(BSIObjectIdentifiers.ecdsa_plain_SHA1, "SHA1WITHPLAIN-ECDSA");
        oids.put(BSIObjectIdentifiers.ecdsa_plain_SHA224, "SHA224WITHPLAIN-ECDSA");
        oids.put(BSIObjectIdentifiers.ecdsa_plain_SHA256, "SHA256WITHPLAIN-ECDSA");
        oids.put(BSIObjectIdentifiers.ecdsa_plain_SHA384, "SHA384WITHPLAIN-ECDSA");
        oids.put(BSIObjectIdentifiers.ecdsa_plain_SHA512, "SHA512WITHPLAIN-ECDSA");
        oids.put(BSIObjectIdentifiers.ecdsa_plain_RIPEMD160, "RIPEMD160WITHPLAIN-ECDSA");
        oids.put(EACObjectIdentifiers.id_TA_ECDSA_SHA_1, "SHA1WITHCVC-ECDSA");
        oids.put(EACObjectIdentifiers.id_TA_ECDSA_SHA_224, "SHA224WITHCVC-ECDSA");
        oids.put(EACObjectIdentifiers.id_TA_ECDSA_SHA_256, "SHA256WITHCVC-ECDSA");
        oids.put(EACObjectIdentifiers.id_TA_ECDSA_SHA_384, "SHA384WITHCVC-ECDSA");
        oids.put(EACObjectIdentifiers.id_TA_ECDSA_SHA_512, "SHA512WITHCVC-ECDSA");
        oids.put(IsaraObjectIdentifiers.id_alg_xmss, "XMSS");
        oids.put(IsaraObjectIdentifiers.id_alg_xmssmt, "XMSSMT");

        oids.put(new ASN1ObjectIdentifier("1.2.840.113549.1.1.4"), "MD5WITHRSA");
        oids.put(new ASN1ObjectIdentifier("1.2.840.113549.1.1.2"), "MD2WITHRSA");
        oids.put(new ASN1ObjectIdentifier("1.2.840.10040.4.3"), "SHA1WITHDSA");
        oids.put(X9ObjectIdentifiers.ecdsa_with_SHA1, "SHA1WITHECDSA");
        oids.put(X9ObjectIdentifiers.ecdsa_with_SHA224, "SHA224WITHECDSA");
        oids.put(X9ObjectIdentifiers.ecdsa_with_SHA256, "SHA256WITHECDSA");
        oids.put(X9ObjectIdentifiers.ecdsa_with_SHA384, "SHA384WITHECDSA");
        oids.put(X9ObjectIdentifiers.ecdsa_with_SHA512, "SHA512WITHECDSA");
        oids.put(OIWObjectIdentifiers.sha1WithRSA, "SHA1WITHRSA");
        oids.put(OIWObjectIdentifiers.dsaWithSHA1, "SHA1WITHDSA");
        oids.put(NISTObjectIdentifiers.dsa_with_sha224, "SHA224WITHDSA");
        oids.put(NISTObjectIdentifiers.dsa_with_sha256, "SHA256WITHDSA");
    }

    private final JcaJceHelper helper;
    private final ProvCrlRevocationChecker checker;

    private PKIXCertRevocationCheckerParameters parameters;
    private boolean isEnabledOCSP;
    private String ocspURL;

    public ProvRevocationChecker(JcaJceHelper helper)
    {
        this.helper = helper;
        this.checker = new ProvCrlRevocationChecker(helper);
    }

    public void setParameter(String name, Object value)
    {

    }

    public void initialize(PKIXCertRevocationCheckerParameters parameters)
    {
        this.parameters = parameters;
        checker.initialize(parameters);

        this.isEnabledOCSP = Properties.isOverrideSet("ocsp.enable");
        this.ocspURL = Properties.getPropertyValue("ocsp.responderURL");
    }

    public List<CertPathValidatorException> getSoftFailExceptions()
    {
        return null;
    }

    public void init(boolean forForward)
        throws CertPathValidatorException
    {
         checker.init(forForward);
    }

    public boolean isForwardCheckingSupported()
    {
        return false;
    }

    public Set<String> getSupportedExtensions()
    {
        return null;
    }

    public void check(Certificate certificate, Collection<String> collection)
        throws CertPathValidatorException
    {
        Map<X509Certificate, byte[]> ocspResponses = this.getOcspResponses();
        X509Certificate cert = (X509Certificate)certificate;

        // only check end-entity certificates.
        if (this.getOptions().contains(Option.ONLY_END_ENTITY) && cert.getBasicConstraints() != -1)
        {
            return;
        }

        URI ocspUri = this.getOcspResponder();
        if (ocspUri == null && this.ocspURL != null)
        {
            try
            {
                ocspUri = new URI(this.ocspURL);
            }
            catch (URISyntaxException e)
            {
                throw new CertPathValidatorException("configuration error: " + e.getMessage(),
                                                    e, parameters.getCertPath(), parameters.getIndex());
            }
        }

        boolean preValidated = false;
        if (ocspResponses.get(cert) == null && ocspUri != null)
        {
            URL ocspUrl;
            try
            {
                ocspUrl = ocspUri.toURL();
            }
            catch (MalformedURLException e)
            {
                throw new CertPathValidatorException("configuration error: " + e.getMessage(),
                                                                    e, parameters.getCertPath(), parameters.getIndex());
            }

            org.bouncycastle.asn1.x509.Certificate issuer = extractCert();

            // TODO: configure hash algorithm
            CertID id = createCertID(new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1), issuer, new ASN1Integer(cert.getSerialNumber()));

            //
            // basic request generation
            //
            ASN1EncodableVector requests = new ASN1EncodableVector();

            requests.add(new Request(id, null));

            List exts = this.getOcspExtensions();
            ASN1EncodableVector requestExtensions = new ASN1EncodableVector();
            byte[] nonce = null;

            for (int i = 0; i != exts.size(); i++)
            {
                Extension ext = (Extension)exts.get(i);
                byte[]    value = ext.getValue();

                if (OCSPObjectIdentifiers.id_pkix_ocsp_nonce.getId().equals(ext.getId()))
                {
                    nonce = value;
                }

                requestExtensions.add(new org.bouncycastle.asn1.x509.Extension(
                    new ASN1ObjectIdentifier(ext.getId()), ext.isCritical(), value));
            }

            // TODO: configure originator
            TBSRequest tbsReq = new TBSRequest(null, new DERSequence(requests),
                                        Extensions.getInstance(new DERSequence(requestExtensions)));

            org.bouncycastle.asn1.ocsp.Signature signature = null;

            try
            {
                byte[] request = new OCSPRequest(tbsReq, signature).getEncoded();

                HttpURLConnection ocspCon = (HttpURLConnection)ocspUrl.openConnection();
                ocspCon.setConnectTimeout(DEFAULT_OCSP_TIMEOUT);
                ocspCon.setReadTimeout(DEFAULT_OCSP_TIMEOUT);
                ocspCon.setDoOutput(true);
                ocspCon.setDoInput(true);
                ocspCon.setRequestMethod("POST");
                ocspCon.setRequestProperty("Content-type", "application/ocsp-request");
                ocspCon.setRequestProperty("Content-length", String.valueOf(request.length));

                OutputStream reqOut = ocspCon.getOutputStream();
                reqOut.write(request);
                reqOut.flush();

                InputStream reqIn = ocspCon.getInputStream();
                int contentLength = ocspCon.getContentLength();
                if (contentLength < 0)
                {
                    // TODO: make configurable
                    contentLength = DEFAULT_OCSP_MAX_RESPONSE_SIZE;
                }
                OCSPResponse response = OCSPResponse.getInstance(Streams.readAllLimited(reqIn, contentLength));

                if (OCSPResponseStatus.SUCCESSFUL == response.getResponseStatus().getValue().intValueExact())
                {
                    ResponseBytes respBytes = ResponseBytes.getInstance(response.getResponseBytes());

                    if (respBytes.getResponseType().equals(OCSPObjectIdentifiers.id_pkix_ocsp_basic))
                    {
                        BasicOCSPResponse basicResp = BasicOCSPResponse.getInstance(respBytes.getResponse().getOctets());

                        preValidated = validatedOcspResponse(basicResp, nonce);
                    }

                    if (!preValidated)
                    {
                        throw new CertPathValidatorException("OCSP response failed to validate");
                    }

                    ocspResponses.put(cert, response.getEncoded());
                }
                else
                {
                    throw new CertPathValidatorException(
                        "OCSP responder failed: " + response.getResponseStatus().getValue(),
                                              null, parameters.getCertPath(), parameters.getIndex());
                }
            }
            catch (IOException e)
            {
                throw new CertPathValidatorException("cannot talk to OCSP responder: " + e.getMessage(), e, parameters.getCertPath(), parameters.getIndex());
            }
        }

        if (!ocspResponses.isEmpty())
        {
            OCSPResponse ocspResponse = OCSPResponse.getInstance(ocspResponses.get(cert));
            ASN1Integer serialNumber = new ASN1Integer(cert.getSerialNumber());

            if (ocspResponse != null)
            {
                if (OCSPResponseStatus.SUCCESSFUL == ocspResponse.getResponseStatus().getValue().intValueExact())
                {
                    ResponseBytes respBytes = ResponseBytes.getInstance(ocspResponse.getResponseBytes());

                    if (respBytes.getResponseType().equals(OCSPObjectIdentifiers.id_pkix_ocsp_basic))
                    {
                        try
                        {
                            BasicOCSPResponse basicResp = BasicOCSPResponse.getInstance(respBytes.getResponse().getOctets());

                            if (preValidated || validatedOcspResponse(basicResp, null))
                            {
                                ResponseData responseData = ResponseData.getInstance(basicResp.getTbsResponseData());

                                ASN1Sequence s = responseData.getResponses();

                                CertID certID = null;
                                for (int i = 0; i != s.size(); i++)
                                {
                                    SingleResponse resp = SingleResponse.getInstance(s.getObjectAt(i));

                                    if (serialNumber.equals(resp.getCertID().getSerialNumber()))
                                    {
                                        if (parameters.getValidDate().before(resp.getThisUpdate().getDate()))
                                        {
                                            throw new ExtCertPathValidatorException("OCSP response date out of range");
                                        }
                                        ASN1GeneralizedTime nextUp = resp.getNextUpdate();
                                        if (nextUp != null && parameters.getValidDate().after(nextUp.getDate()))
                                        {
                                            throw new ExtCertPathValidatorException("OCSP response expired");
                                        }
                                        if (certID == null || !certID.getHashAlgorithm().equals(resp.getCertID().getHashAlgorithm()))
                                        {
                                            org.bouncycastle.asn1.x509.Certificate issuer = extractCert();

                                            certID = createCertID(resp.getCertID(), issuer, serialNumber);
                                        }
                                        if (certID.equals(resp.getCertID()))
                                        {
                                            if (resp.getCertStatus().getTagNo() == 0)
                                            {
                                                // we're good!
                                                return;
                                            }
                                            if (resp.getCertStatus().getTagNo() == 1)
                                            {
                                                RevokedInfo info = RevokedInfo.getInstance(resp.getCertStatus().getStatus());
                                                CRLReason reason = info.getRevocationReason();
                                                throw new CertPathValidatorException(
                                                    "certificate revoked, reason=(" + reason + "), date=" + info.getRevocationTime().getDate(),
                                                    null, parameters.getCertPath(), parameters.getIndex());
                                            }
                                            throw new CertPathValidatorException(
                                                "certificate revoked, details unknown",
                                                null, parameters.getCertPath(), parameters.getIndex());
                                        }
                                    }
                                }
                            }
                        }
                        catch (CertPathValidatorException e)
                        {
                            throw e;
                        }
                        catch (Exception e)
                        {
                            throw new ExtCertPathValidatorException("unable to process OCSP response", e);
                        }
                    }
                }
                else
                {
                    throw new CertPathValidatorException(
                        "OCSP response failed: " + ocspResponse.getResponseStatus().getValue(),
                                              null, parameters.getCertPath(), parameters.getIndex());
                }
            }
        }

        // CRL checks.
        checker.check(certificate);
    }

    private boolean validatedOcspResponse(BasicOCSPResponse basicResp, byte[] nonce)
        throws CertPathValidatorException
    {
        try
        {
            ASN1Sequence certs = basicResp.getCerts();

            Signature sig = helper.createSignature(getSignatureName(basicResp.getSignatureAlgorithm()));

            X509Certificate sigCert = this.getOcspResponderCert();
            if (sigCert == null && certs == null)
            {
                throw new CertPathValidatorException("OCSP responder certificate not found");
            }

            if (sigCert != null)
            {
                sig.initVerify(sigCert);
            }
            else
            {
                CertificateFactory cf = helper.createCertificateFactory("X.509");

                X509Certificate ocspCert = (X509Certificate)cf.generateCertificate(new ByteArrayInputStream(certs.getObjectAt(0).toASN1Primitive().getEncoded()));

                // check we are signed by CA
                ocspCert.verify(parameters.getSigningCert().getPublicKey());

                // check we are valid
                List extendedKeyUsage = ocspCert.getExtendedKeyUsage();
                if (extendedKeyUsage == null || !extendedKeyUsage.contains(KeyPurposeId.id_kp_OCSPSigning.getId()))
                {
                    throw new CertPathValidatorException("responder certificate not valid for signing OCSP responses", null,
                        parameters.getCertPath(), parameters.getIndex());
                }

                sig.initVerify(ocspCert);
            }

            sig.update(basicResp.getTbsResponseData().getEncoded(ASN1Encoding.DER));

            if (sig.verify(basicResp.getSignature().getBytes()))
            {
                if (nonce != null)
                {
                    Extensions exts = basicResp.getTbsResponseData().getResponseExtensions();

                    org.bouncycastle.asn1.x509.Extension ext = exts.getExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce);

                    if (!Arrays.areEqual(nonce, ext.getExtnValue().getOctets()))
                    {
                        throw new CertPathValidatorException("nonce mismatch in OCSP response", null, parameters.getCertPath(), parameters.getIndex());
                    }
                }
                return true;
            }

            return false;
        }
        catch (CertPathValidatorException e)
        {
            throw e;
        }
        catch (GeneralSecurityException e)
        {
            throw new CertPathValidatorException("OCSP response failure: " + e.getMessage(), e, parameters.getCertPath(), parameters.getIndex());
        }
        catch (IOException e)
        {
            throw new CertPathValidatorException("OCSP response failure: " + e.getMessage(), e, parameters.getCertPath(), parameters.getIndex());
        }
    }

    private org.bouncycastle.asn1.x509.Certificate extractCert()
        throws CertPathValidatorException
    {
        try
        {
            return org.bouncycastle.asn1.x509.Certificate.getInstance(parameters.getSigningCert().getEncoded());
        }
        catch (Exception e)
        {
            throw new CertPathValidatorException("cannot process signing cert: " + e.getMessage(), e, parameters.getCertPath(), parameters.getIndex());
        }
    }

    public void check(Certificate certificate)
        throws CertPathValidatorException
    {
         this.check(certificate, null);
    }

    private CertID createCertID(CertID base, org.bouncycastle.asn1.x509.Certificate issuer, ASN1Integer serialNumber)
        throws CertPathValidatorException
    {
        return createCertID(base.getHashAlgorithm(), issuer, serialNumber);
    }

    private CertID createCertID(AlgorithmIdentifier digestAlg, org.bouncycastle.asn1.x509.Certificate issuer, ASN1Integer serialNumber)
        throws CertPathValidatorException
    {
        try
        {
            MessageDigest digest = helper.createMessageDigest(MessageDigestUtils.getDigestName(digestAlg.getAlgorithm()));

            ASN1OctetString issuerNameHash = new DEROctetString(digest.digest(issuer.getSubject().getEncoded(ASN1Encoding.DER)));

            ASN1OctetString issuerKeyHash = new DEROctetString(digest.digest(
                                                    issuer.getSubjectPublicKeyInfo().getPublicKeyData().getBytes()));

            return new CertID(digestAlg, issuerNameHash, issuerKeyHash, serialNumber);
        }
        catch (Exception e)
        {
            throw new CertPathValidatorException("problem creating ID: " + e, e);
        }
    }

    // we need to remove the - to create a correct signature name
    private static String getDigestName(ASN1ObjectIdentifier oid)
    {
        String name = MessageDigestUtils.getDigestName(oid);

        int dIndex = name.indexOf('-');
        if (dIndex > 0 && !name.startsWith("SHA3"))
        {
            return name.substring(0, dIndex) + name.substring(dIndex + 1);
        }

        return name;
    }

    private static String getSignatureName(
        AlgorithmIdentifier sigAlgId)
    {
        ASN1Encodable params = sigAlgId.getParameters();

        if (params != null && !DERNull.INSTANCE.equals(params))
        {
            if (sigAlgId.getAlgorithm().equals(PKCSObjectIdentifiers.id_RSASSA_PSS))
            {
                RSASSAPSSparams rsaParams = RSASSAPSSparams.getInstance(params);
                return getDigestName(rsaParams.getHashAlgorithm().getAlgorithm()) + "WITHRSAANDMGF1";
            }
        }

        if (oids.containsKey(sigAlgId.getAlgorithm()))
        {
            return (String)oids.get(sigAlgId.getAlgorithm());
        }

        return sigAlgId.getAlgorithm().getId();
    }
}
