package docuement.magazin;

import docuement.Document;

public class Magazine extends Document {

    private int numeroEdition;
    private String mois;
    public Magazine(String id, String title, int anneePublication, boolean estEmprunte, int numeroEdition, String mois) {
        super(id, title, anneePublication, estEmprunte);
        this.numeroEdition = numeroEdition;
        this.mois = mois;

    }

    @Override
    public void affichage() {
        super.affichage();
        System.out.println(this);
    }

    @Override
    public String toString() {
        return "Neumero Edition: " + numeroEdition + " Mois" + mois;

    }

    @Override
    public String getType() {
        return "Magazine";
    }

    @Override
    public int getDureeEmpruntMax() {
        return 7;
    }
}
