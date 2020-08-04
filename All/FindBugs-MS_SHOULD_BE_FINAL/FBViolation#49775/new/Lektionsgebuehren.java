package ch.metzenthin.svm.persistence.entities;

import javax.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * @author Martin Schraner
 */
@Entity
@Table(name="Lektionsgebuehren")
public class Lektionsgebuehren implements Comparable<Lektionsgebuehren> {

    public static final int MAX_KINDER = 6;
    public static final int MIN_ANZAHL_TAGE_SECHS_JAHRES_RABATT = 2125;   // 5 Jahre und 10 Monate

    @Id
    @Column(name = "lektionslaenge")
    private Integer lektionslaenge;

    @Version
    @Column(name = "last_updated")
    private Timestamp version;

    @Column(name = "betrag_1_kind", nullable = false)
    private BigDecimal betrag1Kind;

    @Column(name = "betrag_2_kinder", nullable = false)
    private BigDecimal betrag2Kinder;

    @Column(name = "betrag_3_kinder", nullable = false)
    private BigDecimal betrag3Kinder;

    @Column(name = "betrag_4_kinder", nullable = false)
    private BigDecimal betrag4Kinder;

    @Column(name = "betrag_5_kinder", nullable = false)
    private BigDecimal betrag5Kinder;

    @Column(name = "betrag_6_kinder", nullable = false)
    private BigDecimal betrag6Kinder;

    public Lektionsgebuehren() {
    }

    public Lektionsgebuehren(Integer lektionslaenge, BigDecimal betrag1Kind, BigDecimal betrag2Kinder, BigDecimal betrag3Kinder, BigDecimal betrag4Kinder, BigDecimal betrag5Kinder, BigDecimal betrag6Kinder) {
        this.lektionslaenge = lektionslaenge;
        this.betrag1Kind = betrag1Kind;
        this.betrag2Kinder = betrag2Kinder;
        this.betrag3Kinder = betrag3Kinder;
        this.betrag4Kinder = betrag4Kinder;
        this.betrag5Kinder = betrag5Kinder;
        this.betrag6Kinder = betrag6Kinder;
    }

    public boolean isIdenticalWith(Lektionsgebuehren otherLektionsgebuehren) {
        return otherLektionsgebuehren != null
                && (lektionslaenge.equals(otherLektionsgebuehren.lektionslaenge));
    }

    public void copyAttributesFrom(Lektionsgebuehren otherLektionslaengen) {
        this.betrag1Kind = otherLektionslaengen.betrag1Kind;
        this.betrag2Kinder = otherLektionslaengen.betrag2Kinder;
        this.betrag3Kinder = otherLektionslaengen.betrag3Kinder;
        this.betrag4Kinder = otherLektionslaengen.betrag4Kinder;
        this.betrag5Kinder = otherLektionslaengen.betrag5Kinder;
        this.betrag6Kinder = otherLektionslaengen.betrag6Kinder;
    }

    @Override
    public int compareTo(Lektionsgebuehren otherLektionsgebuehren) {
        return lektionslaenge.compareTo(otherLektionsgebuehren.getLektionslaenge());
    }

    public Integer getLektionslaenge() {
        return lektionslaenge;
    }

    public void setLektionslaenge(Integer lektionslaenge) {
        this.lektionslaenge = lektionslaenge;
    }

    public BigDecimal getBetrag1Kind() {
        return betrag1Kind;
    }

    public void setBetrag1Kind(BigDecimal betrag1Kind) {
        this.betrag1Kind = betrag1Kind;
    }

    public BigDecimal getBetrag2Kinder() {
        return betrag2Kinder;
    }

    public void setBetrag2Kinder(BigDecimal betrag2Kinder) {
        this.betrag2Kinder = betrag2Kinder;
    }

    public BigDecimal getBetrag3Kinder() {
        return betrag3Kinder;
    }

    public void setBetrag3Kinder(BigDecimal betrag3Kinder) {
        this.betrag3Kinder = betrag3Kinder;
    }

    public BigDecimal getBetrag4Kinder() {
        return betrag4Kinder;
    }

    public void setBetrag4Kinder(BigDecimal betrag4Kinder) {
        this.betrag4Kinder = betrag4Kinder;
    }

    public BigDecimal getBetrag5Kinder() {
        return betrag5Kinder;
    }

    public void setBetrag5Kinder(BigDecimal betrag5Kinder) {
        this.betrag5Kinder = betrag5Kinder;
    }

    public BigDecimal getBetrag6Kinder() {
        return betrag6Kinder;
    }

    public void setBetrag6Kinder(BigDecimal betrag6Kinder) {
        this.betrag6Kinder = betrag6Kinder;
    }
}
