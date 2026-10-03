package docuement;

public abstract class Document {
    private String id;
    private String title;
    private int anneePublication;
    private boolean estEmprunte;

    public Document(String id, String title, int anneePublication, boolean estEmprunte) {
        this.id = id;
        this.title = title;
        this.anneePublication = anneePublication;
        this.estEmprunte = estEmprunte;
    }

    public Document() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getAnneePublication() {
        return anneePublication;
    }

    public void setAnneePublication(int anneePublication) {
        this.anneePublication = anneePublication;
    }

    public boolean isEstEmprunte() {
        return estEmprunte;
    }

    public void setEstEmprunte(boolean estEmprunte) {
        this.estEmprunte = estEmprunte;
    }

    public void affichage() {
        System.out.println(this);
    }

    @Override
    public String toString() {
        return "Document: " + getId() + ", Document Title: " + title + ", Document Annee Publication: " + getAnneePublication();
    }


    // Abstract Methods needs Implementation
    public abstract String getType();

    public abstract int getDureeEmpruntMax();

}
