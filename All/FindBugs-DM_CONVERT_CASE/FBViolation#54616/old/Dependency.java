package org.codesecure.dependencycheck.dependency;
/*
 * This file is part of DependencyCheck.
 *
 * DependencyCheck is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * DependencyCheck is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DependencyCheck. If not, see http://www.gnu.org/licenses/.
 *
 * Copyright (c) 2012 Jeremy Long. All Rights Reserved.
 */

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codesecure.dependencycheck.utils.Checksum;
import org.codesecure.dependencycheck.utils.FileUtils;

/**
 * A program dependency. This object is one of the core components within
 * DependencyCheck. It is used to collect information about the dependency in
 * the form of evidence. The Evidence is then used to determine if there are any
 * known, published, vulnerabilities associated with the program dependency.
 *
 * @author Jeremy Long (jeremy.long@gmail.com)
 */
public class Dependency {

    /**
     * The actual file path of the dependency on disk.
     */
    private String actualFilePath = null;
    /**
     * The file path to display.
     */
    private String filePath = null;
    /**
     * The file name of the dependency.
     */
    private String fileName = null;
    /**
     * The file extension of the dependency.
     */
    private String fileExtension = null;
    /**
     * The md5 hash of the dependency.
     */
    private String md5sum = null;
    /**
     * The SHA1 hash of the dependency.
     */
    private String sha1sum = null;
    /**
     * A list of Identifiers.
     */
    private List<Identifier> identifiers = null;
    /**
     * A collection of vendor evidence.
     */
    protected EvidenceCollection vendorEvidence = null;
    /**
     * A collection of product evidence.
     */
    protected EvidenceCollection productEvidence = null;
    /**
     * A collection of version evidence.
     */
    protected EvidenceCollection versionEvidence = null;

    /**
     * Constructs a new Dependency object.
     */
    public Dependency() {
        vendorEvidence = new EvidenceCollection();
        productEvidence = new EvidenceCollection();
        versionEvidence = new EvidenceCollection();
        identifiers = new ArrayList<Identifier>();
        vulnerabilities = new ArrayList<Vulnerability>();
    }

    /**
     * Constructs a new Dependency object.
     *
     * @param file the File to create the dependency object from.
     */
    public Dependency(File file) {
        this();
        this.actualFilePath = file.getPath();
        this.filePath = this.actualFilePath;
        this.fileName = file.getName();
        this.fileExtension = FileUtils.getFileExtension(fileName);
        determineHashes(file);
    }

    /**
     * Returns the file name of the dependency.
     *
     * @return the file name of the dependency.
     */
    public String getFileName() {
        return this.fileName;
    }

    /**
     * Sets the file name of the dependency.
     *
     * @param fileName the file name of the dependency.
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * Sets the actual file path of the dependency on disk.
     *
     * @param actualFilePath the file path of the dependency.
     */
    public void setActualFilePath(String actualFilePath) {
        this.actualFilePath = actualFilePath;
    }

    /**
     * Gets the file path of the dependency.
     *
     * @return the file path of the dependency.
     */
    public String getActualFilePath() {
        return this.actualFilePath;
    }

    /**
     * Sets the file path of the dependency.
     *
     * @param filePath the file path of the dependency.
     */
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    /**
     * <p>Gets the file path of the dependency.</p> <p><b>NOTE:</b> This may not
     * be the actual path of the file on disk. The actual path of the file on
     * disk can be obtained via the getActualFilePath().</p>
     *
     * @return the file path of the dependency.
     */
    public String getFilePath() {
        return this.filePath;
    }

    /**
     * Sets the file name of the dependency.
     *
     * @param fileExtension the file name of the dependency.
     */
    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    /**
     * Gets the file extension of the dependency.
     *
     * @return the file extension of the dependency.
     */
    public String getFileExtension() {
        return this.fileExtension;
    }

    /**
     * Returns the MD5 Checksum of the dependency file.
     *
     * @return the MD5 Checksum
     */
    public String getMd5sum() {
        return this.md5sum;
    }

    /**
     * Sets the MD5 Checksum of the dependency.
     *
     * @param md5sum the MD5 Checksum
     */
    public void setMd5sum(String md5sum) {
        this.md5sum = md5sum;
    }

    /**
     * Returns the SHA1 Checksum of the dependency.
     *
     * @return the SHA1 Checksum
     */
    public String getSha1sum() {
        return this.sha1sum;
    }

    /**
     * Sets the SHA1 Checksum of the dependency.
     *
     * @param sha1sum the SHA1 Checksum
     */
    public void setSha1sum(String sha1sum) {
        this.sha1sum = sha1sum;
    }

    /**
     * Returns a List of Identifiers.
     *
     * @return an ArrayList of Identifiers.
     */
    public List<Identifier> getIdentifiers() {
        return this.identifiers;
    }

    /**
     * Sets a List of Identifiers.
     *
     * @param identifiers A list of Identifiers.
     */
    public void setIdentifiers(List<Identifier> identifiers) {
        this.identifiers = identifiers;
    }

    /**
     * Adds an entry to the list of detected Identifiers for the dependency
     * file.
     *
     * @param type the type of identifier (such as CPE).
     * @param value the value of the identifier.
     * @param url the URL of the identifier.
     */
    public void addIdentifier(String type, String value, String url) {
        Identifier i = new Identifier(type, value, url);
        this.identifiers.add(i);
    }

    /**
     * Returns the evidence used to identify this dependency.
     *
     * @return an EvidenceCollection.
     */
    public EvidenceCollection getEvidence() {
        return EvidenceCollection.merge(this.productEvidence, this.vendorEvidence, this.versionEvidence);
    }

    /**
     * Returns the evidence used to identify this dependency.
     *
     * @return an EvidenceCollection.
     */
    public EvidenceCollection getEvidenceUsed() {
        return EvidenceCollection.mergeUsed(this.productEvidence, this.vendorEvidence, this.versionEvidence);
    }

    /**
     * Gets the Vendor Evidence.
     *
     * @return an EvidenceCollection.
     */
    public EvidenceCollection getVendorEvidence() {
        return this.vendorEvidence;
    }

    /**
     * Gets the Product Evidence.
     *
     * @return an EvidenceCollection.
     */
    public EvidenceCollection getProductEvidence() {
        return this.productEvidence;
    }

    /**
     * Gets the Version Evidence.
     *
     * @return an EvidenceCollection.
     */
    public EvidenceCollection getVersionEvidence() {
        return this.versionEvidence;
    }
    /**
     * A list of exceptions that occured during analysis of this dependency.
     */
    protected List<Exception> analysisExceptions = new ArrayList<Exception>();

    /**
     * Get the value of analysisExceptions
     *
     * @return the value of analysisExceptions
     */
    public List<Exception> getAnalysisExceptions() {
        return analysisExceptions;
    }

    /**
     * Set the value of analysisExceptions
     *
     * @param analysisExceptions new value of analysisExceptions
     */
    public void setAnalysisExceptions(List<Exception> analysisExceptions) {
        this.analysisExceptions = analysisExceptions;
    }

    /**
     * Adds an exception to the analysis exceptions collection.
     *
     * @param ex an exception.
     */
    public void addAnalysisException(Exception ex) {
        this.analysisExceptions.add(ex);
    }
    /**
     * The description of the JAR file.
     */
    protected String description;

    /**
     * Get the value of description
     *
     * @return the value of description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Set the value of description
     *
     * @param description new value of description
     */
    public void setDescription(String description) {
        this.description = description;
    }
    /**
     * The license that this dependency uses.
     */
    private String license;

    /**
     * Get the value of license
     *
     * @return the value of license
     */
    public String getLicense() {
        return license;
    }

    /**
     * Set the value of license
     *
     * @param license new value of license
     */
    public void setLicense(String license) {
        this.license = license;
    }

    /**
     * Determines if the specified string was used when searching.
     *
     * @param str is the string that is being checked if it was used.
     * @return true or false.
     */
    public boolean containsUsedString(String str) {
        if (str == null) {
            return false;
        }

        String fnd = str.toLowerCase();

        if (vendorEvidence.containsUsedString(str)) {
            return true;
        }
        if (productEvidence.containsUsedString(str)) {
            return true;
        }
        if (versionEvidence.containsUsedString(fnd)) {
            return true;
        }
        return false;
    }
    /**
     * A list of vulnerabilities for this dependency
     */
    private List<Vulnerability> vulnerabilities;

    /**
     * Get the list of vulnerabilities
     *
     * @return the list of vulnerabilities
     */
    public List<Vulnerability> getVulnerabilities() {
        return vulnerabilities;
    }

    /**
     * Set the value of vulnerabilities
     *
     * @param vulnerabilities new value of vulnerabilities
     */
    public void setVulnerabilities(List<Vulnerability> vulnerabilities) {
        this.vulnerabilities = vulnerabilities;
    }

    private void determineHashes(File file) {
        String md5 = null;
        String sha1 = null;
        try {
            md5 = Checksum.getMD5Checksum(file);
            sha1 = Checksum.getSHA1Checksum(file);
        } catch (IOException ex) {
            Logger.getLogger(Dependency.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Dependency.class.getName()).log(Level.SEVERE, null, ex);
        }
        this.setMd5sum(md5);
        this.setSha1sum(sha1);
    }

    /**
     * Adds a vulnerability to the dependency.
     *
     * @param vulnerability a vulnerability outlining a vulnerability.
     */
    public void addVulnerability(Vulnerability vulnerability) {
        this.vulnerabilities.add(vulnerability);
    }
}
