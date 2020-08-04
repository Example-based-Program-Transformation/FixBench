package ch.metzenthin.svm.domain.model;

import ch.metzenthin.svm.common.dataTypes.Field;
import ch.metzenthin.svm.common.dataTypes.Listentyp;
import ch.metzenthin.svm.common.utils.SvmProperties;
import ch.metzenthin.svm.domain.SvmRequiredException;
import ch.metzenthin.svm.domain.SvmValidationException;
import ch.metzenthin.svm.domain.commands.*;
import ch.metzenthin.svm.persistence.entities.Kurs;
import ch.metzenthin.svm.ui.componentmodel.KurseTableModel;
import ch.metzenthin.svm.ui.componentmodel.LehrkraefteTableModel;
import ch.metzenthin.svm.ui.componentmodel.SchuelerSuchenTableModel;

import java.io.File;
import java.util.Properties;

import static ch.metzenthin.svm.common.utils.Converter.asString;

/**
 * @author Martin Schraner
 */
public class ListenExportModelImpl extends AbstractModel implements ListenExportModel {
    
    private Listentyp listentyp;
    private String titel;
    private File templateFile;
    
    public ListenExportModelImpl(CommandInvoker commandInvoker) {
        super(commandInvoker);
    }

    @Override
    public Listentyp getListentyp() {
        return listentyp;
    }

    @Override
    public void setListentyp(Listentyp listentyp) throws SvmRequiredException {
        Listentyp oldValue = this.listentyp;
        this.listentyp = listentyp;
        firePropertyChange(Field.LISTENTYP, oldValue, this.listentyp);
        if (listentyp == null) {
            invalidate();
            throw new SvmRequiredException(Field.LISTENTYP);
        }
    }

    private final StringModelAttribute titelModelAttribute = new StringModelAttribute(
            this,
            Field.TITEL, 2, 110,
            new AttributeAccessor<String>() {
                @Override
                public String getValue() {
                    return titel;
                }

                @Override
                public void setValue(String value) {
                    titel = value;
                }
            }
    );

    @Override
    public String getTitel() {
        return titelModelAttribute.getValue();
    }

    @Override
    public File getTemplateFile() {
        return templateFile;
    }

    @Override
    public void setTitel(String titel) throws SvmValidationException {
        titelModelAttribute.setNewValue(true, titel, isBulkUpdate());
    }

    @Override
    public File getSaveFileInit() {
        Properties prop = SvmProperties.getSvmProperties();
        File listenDirectoryInit = new File(prop.getProperty(SvmProperties.KEY_DEFAULT_OUTPUT_DIRECTORY) + File.separator);
        if (!listenDirectoryInit.exists()) {
            boolean success = listenDirectoryInit.mkdirs();
            if (!success) {
                return null;
            }
        }
        String outputFile = listentyp + "." + listentyp.getFiletyp().getFileExtension();
        outputFile = outputFile.replaceAll("\\p{Blank}", "_");
        outputFile = outputFile.replaceAll("ä", "ae");
        outputFile = outputFile.replaceAll("ö", "oe");
        outputFile = outputFile.replaceAll("ü", "ue");
        return new File(listenDirectoryInit.getAbsolutePath() + File.separator + outputFile);
    }

    @Override
    public CreateListeCommand.Result createListenFile(File outputFile, SchuelerSuchenTableModel schuelerSuchenTableModel, LehrkraefteTableModel lehrkraefteTableModel, KurseTableModel kurseTableModel) {
        CommandInvoker commandInvoker = getCommandInvoker();
        CreateListeCommand.Result result = null;
        switch (listentyp) {
            case SCHUELER_ADRESSLISTE:
                CreateSchuelerAdresslisteCommand createSchuelerAdresslisteCommand = new CreateSchuelerAdresslisteCommand(schuelerSuchenTableModel, titel, outputFile);
                commandInvoker.executeCommand(createSchuelerAdresslisteCommand);
                result = createSchuelerAdresslisteCommand.getResult();
                break;
            case SCHUELER_ABSENZENLISTE:
                CreateListeFromTemplateCommand createListeFromTemplateCommand = new CreateListeFromTemplateCommand(schuelerSuchenTableModel, titel, listentyp, outputFile);
                commandInvoker.executeCommand(createListeFromTemplateCommand);
                templateFile = createListeFromTemplateCommand.getTemplateFile();
                result = createListeFromTemplateCommand.getResult();
                break;
            case SCHUELER_ADRESSETIKETTEN:
                CreateAdressenCsvFileCommand createAdressenCsvFileCommandSchueler = new CreateAdressenCsvFileCommand(schuelerSuchenTableModel.getSchuelerList(), outputFile);
                commandInvoker.executeCommand(createAdressenCsvFileCommandSchueler);
                result = createAdressenCsvFileCommandSchueler.getResult();
                break;
            case ROLLENLISTE:
                CreateRollenlisteCommand createRollenlisteCommand = new CreateRollenlisteCommand(schuelerSuchenTableModel, titel, outputFile);
                commandInvoker.executeCommand(createRollenlisteCommand);
                result = createRollenlisteCommand.getResult();
                break;
            case ELTERNMITHILFE_LISTE:
                CreateElternmithilfeListeCommand createElternmithilfeListeCommand = new CreateElternmithilfeListeCommand(schuelerSuchenTableModel, titel, outputFile);
                commandInvoker.executeCommand(createElternmithilfeListeCommand);
                result = createElternmithilfeListeCommand.getResult();
                break;
            case LEHRKRAEFTE_ADRESSLISTE:
                CreateLehrkraefteAdresslisteCommand createLehrkraefteAdresslisteCommand = new CreateLehrkraefteAdresslisteCommand(lehrkraefteTableModel, titel, outputFile);
                commandInvoker.executeCommand(createLehrkraefteAdresslisteCommand);
                result = createLehrkraefteAdresslisteCommand.getResult();
                break;
            case LEHRKRAEFTE_ADRESSETIKETTEN:
                CreateAdressenCsvFileCommand createAdressenCsvFileCommandLehrkraefte = new CreateAdressenCsvFileCommand(lehrkraefteTableModel.getLehrkraefte(), outputFile);
                commandInvoker.executeCommand(createAdressenCsvFileCommandLehrkraefte);
                result = createAdressenCsvFileCommandLehrkraefte.getResult();
                break;
            case KURSELISTE:
                CreateKurselisteCommand kurselisteCommand = new CreateKurselisteCommand(kurseTableModel, titel, outputFile);
                commandInvoker.executeCommand(kurselisteCommand);
                result = kurselisteCommand.getResult();
                break;
        }
        return result;
    }

    @Override
    public String getTitleInit(SchuelerSuchenTableModel schuelerSuchenTableModel) {
        String titleInit = "";
        switch (listentyp) {
            case SCHUELER_ADRESSLISTE:
                if (schuelerSuchenTableModel.getLehrkraft() != null) {
                    titleInit = getTitleSpecificKurs(schuelerSuchenTableModel);
                } else {
                    titleInit = "Adressliste";
                }
                break;
            case SCHUELER_ABSENZENLISTE:
                titleInit = getTitleSpecificKurs(schuelerSuchenTableModel);
                break;
            case SCHUELER_ADRESSETIKETTEN:
                break;
            case ROLLENLISTE:
                titleInit = getTitleMaerchen(schuelerSuchenTableModel) + ": Rollenliste";
                if (schuelerSuchenTableModel.getGruppe() != null) {
                    titleInit = titleInit + " Gruppe " + schuelerSuchenTableModel.getGruppe().toString();
                }
                break;
            case ELTERNMITHILFE_LISTE:
                titleInit = getTitleMaerchen(schuelerSuchenTableModel) + ": Eltern-Mithilfe";
                if (schuelerSuchenTableModel.getElternmithilfeCode() != null) {
                    titleInit = titleInit + " " + schuelerSuchenTableModel.getElternmithilfeCode().getBeschreibung();
                }
                if (schuelerSuchenTableModel.getGruppe() != null) {
                    titleInit = titleInit + " Gruppe " + schuelerSuchenTableModel.getGruppe().toString();
                }
                break;
            case LEHRKRAEFTE_ADRESSLISTE:
                titleInit = "Lehrkräfte";
                break;
            case LEHRKRAEFTE_ADRESSETIKETTEN:
                break;
            case KURSELISTE:
                titleInit = "Kurse";
                break;
        }
        return titleInit;
    }

    private String getTitleSpecificKurs(SchuelerSuchenTableModel schuelerSuchenTableModel) {
        if (schuelerSuchenTableModel.getWochentag() != null && schuelerSuchenTableModel.getZeitBeginn() != null) {
            if (schuelerSuchenTableModel.getSchuelerList().size() > 0) {
                String lehrkraefte = schuelerSuchenTableModel.getSchuelerList().get(0).getKurseAsList().get(0).getLehrkraefteAsStr();
                String zeitEnde = asString(schuelerSuchenTableModel.getSchuelerList().get(0).getKurseAsList().get(0).getZeitEnde());
                String kursort = schuelerSuchenTableModel.getSchuelerList().get(0).getKurseAsList().get(0).getKursort().getBezeichnung();
                return lehrkraefte + " (" + schuelerSuchenTableModel.getWochentag() + " " + asString(schuelerSuchenTableModel.getZeitBeginn()) + "-" + zeitEnde + ", " + kursort + ")";
            } else {
                CommandInvoker commandInvoker = getCommandInvoker();
                FindKursCommand findKursCommand = new FindKursCommand(schuelerSuchenTableModel.getSemester(), schuelerSuchenTableModel.getWochentag(), schuelerSuchenTableModel.getZeitBeginn(), schuelerSuchenTableModel.getLehrkraft());
                commandInvoker.executeCommand(findKursCommand);
                if (findKursCommand.getResult() == FindKursCommand.Result.KURS_EXISTIERT_NICHT) {
                    return "";
                }
                Kurs kursFound = findKursCommand.getKursFound();
                return kursFound.getLehrkraefteAsStr() + " (" + kursFound.getWochentag() +  " " + asString(kursFound.getZeitBeginn()) + "-" + asString(kursFound.getZeitEnde()) + ", " + kursFound.getKursort() + ")";
            }
        } else {
            return schuelerSuchenTableModel.getLehrkraft().toString();
        }
    }

    private String getTitleMaerchen(SchuelerSuchenTableModel schuelerSuchenTableModel) {
        return schuelerSuchenTableModel.getMaerchen().getBezeichnung();
    }

    @Override
    public boolean isCompleted() {
        return true;
    }

    @Override
    void doValidate() throws SvmValidationException {}
}
