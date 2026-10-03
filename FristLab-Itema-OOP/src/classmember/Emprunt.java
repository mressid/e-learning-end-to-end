package classmember;

import docuement.Document;

import java.time.LocalDate;

public class Emprunt {
    public String idEmbprunt;
    public String membere;
    public Document document;
    public LocalDate dateEmprunt;
    public LocalDate dateRetoureRP;
    public LocalDate dateRetourEffectif;
    public double amende;

    public Emprunt(String idEmprunt, String membre, Document document, LocalDate localDate, LocalDate dateRetourPrevu, LocalDate dateRetourEffectif, Double amende) {
        this.idEmbprunt = idEmbprunt;
        this.membere = membere;
        this.document = document;
        this.dateEmprunt = dateEmprunt;
        this.dateRetoureRP = dateRetoureRP;
        this.dateRetourEffectif = dateRetourEffectif;
        this.amende = amende;
    }

    public Boolean estEnRedard() {
        return LocalDate.now().isAfter(this.dateRetoureRP);
    }
    public long getJoursRetard() {
        return LocalDate.now().toEpochDay() - this.dateRetoureRP.toEpochDay();
    }

    public double calculerAmende() {
        this.amende = getJoursRetard() * 0.5;
        return this.amende;
    }

}
