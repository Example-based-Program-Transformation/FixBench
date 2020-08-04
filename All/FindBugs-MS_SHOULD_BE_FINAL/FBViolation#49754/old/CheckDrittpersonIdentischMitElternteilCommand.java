package ch.metzenthin.svm.domain.commands;

import ch.metzenthin.svm.persistence.entities.Angehoeriger;

/**
 * @author Martin Schraner
 */
public class CheckDrittpersonIdentischMitElternteilCommand implements Command {

    private Angehoeriger mutter;
    private Angehoeriger vater;
    private Angehoeriger rechnungsempfaengerDrittperson;

    public static String ERROR_IDENTISCH_MIT_MUTTER = "Rechnungsempfänger Drittperson ist identisch mit Mutter. Setze Mutter als Rechnungsempfängerin.";
    public static String ERROR_WAHRSCHEINLICH_IDENTISCH_MIT_MUTTER = "Rechnungsempfänger Drittperson und Mutter scheinen identisch zu sein. Setze Mutter als Rechnungsempfängerin und/oder korrigiere Mutter.";
    public static String ERROR_IDENTISCH_MIT_VATER = "Rechnungsempfänger Drittperson ist identisch mit Vater. Setze Vater als Rechnungsempfänger.";
    public static String ERROR_WAHRSCHEINLICH_IDENTISCH_MIT_VATER = "Rechnungsempfänger Drittperson und Vater scheinen identisch zu sein. Setze Vater als Rechnungsempfänger und/oder korrigiere Vater.";

    // output
    private boolean identical;
    private String errorMessage;

    public CheckDrittpersonIdentischMitElternteilCommand(Angehoeriger mutter, Angehoeriger vater, Angehoeriger rechnungsempfaengerDrittperson) {
        this.mutter = mutter;
        this.vater = vater;
        this.rechnungsempfaengerDrittperson = rechnungsempfaengerDrittperson;
    }

    @Override
    public void execute() {

        if (rechnungsempfaengerDrittperson == null || rechnungsempfaengerDrittperson.isEmpty()) {
            identical = false;
            errorMessage = "";
        }

        else if (mutter != null && mutter.getVorname() != null && !mutter.getVorname().trim().isEmpty() && mutter.isIdenticalWith(rechnungsempfaengerDrittperson)) {
            identical = true;
            errorMessage = ERROR_IDENTISCH_MIT_MUTTER;
        }

        else if (mutter != null && mutter.getVorname() != null && !mutter.getVorname().trim().isEmpty() && mutter.isPartOf(rechnungsempfaengerDrittperson)) {
            identical = true;
            errorMessage = ERROR_WAHRSCHEINLICH_IDENTISCH_MIT_MUTTER;
        }

        else if (vater != null && vater.getVorname() != null && !vater.getVorname().trim().isEmpty() && vater.isIdenticalWith(rechnungsempfaengerDrittperson)) {
            identical = true;
            errorMessage = ERROR_IDENTISCH_MIT_VATER;
        }

        else if (vater != null && vater.getVorname() != null && !vater.getVorname().trim().isEmpty() && vater.isPartOf(rechnungsempfaengerDrittperson)) {
            identical = true;
            errorMessage = ERROR_WAHRSCHEINLICH_IDENTISCH_MIT_VATER;
        }

        else {
            identical = false;
            errorMessage = "";
        }

    }

    public boolean isIdentical() {
        return identical;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
