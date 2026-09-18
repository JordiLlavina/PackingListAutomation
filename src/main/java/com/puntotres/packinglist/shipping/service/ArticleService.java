package com.puntotres.packinglist.shipping.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.puntotres.packinglist.shipping.model.Article;
import com.puntotres.packinglist.shipping.model.ArticleRepository;

/** Mantenimiento del catálogo aduanero de artículos. */
@Service
public class ArticleService {

    private final ArticleRepository repo;

    public ArticleService(ArticleRepository repo) { this.repo = repo; }

    public List<Article> todos() { return repo.findAllByOrderByReferenciaAsc(); }
    public List<Article> activos() { return repo.findByActivoTrueOrderByReferenciaAsc(); }
    public Optional<Article> porId(Long id) { return id == null ? Optional.empty() : repo.findById(id); }

    @Transactional
    public Article guardar(Long id, String referencia, String descripcion, String codigoHs, String paisOrigen,
                           Double pesoNeto, Double pesoBruto, BigDecimal valor) {
        validar(referencia, descripcion, codigoHs, paisOrigen);
        Optional<Article> mismo = repo.findByReferenciaIgnoreCase(referencia.trim());
        if (mismo.isPresent() && (id == null || !id.equals(mismo.get().getId()))) throw new DatoInvalidoException("Ya existe la referencia " + referencia.trim() + ".");
        Article articulo = id == null ? null : repo.findById(id).orElse(null);
        if (articulo == null) articulo = new Article(referencia.trim(), descripcion.trim(), codigoHs.trim(), paisOrigen.trim().toUpperCase());
        return repo.save(rellenar(articulo, referencia, descripcion, codigoHs, paisOrigen, pesoNeto, pesoBruto, valor));
    }

    /** Para el importador: la referencia manda; si ya existe, se actualiza. */
    @Transactional
    public Article guardarPorReferencia(String referencia, String descripcion, String codigoHs, String paisOrigen,
                                        Double pesoNeto, Double pesoBruto, BigDecimal valor) {
        validar(referencia, descripcion, codigoHs, paisOrigen);
        Article articulo = repo.findByReferenciaIgnoreCase(referencia.trim())
                .orElseGet(() -> new Article(referencia.trim(), descripcion.trim(), codigoHs.trim(), paisOrigen.trim().toUpperCase()));
        return repo.save(rellenar(articulo, referencia, descripcion, codigoHs, paisOrigen, pesoNeto, pesoBruto, valor));
    }

    @Transactional
    public void activar(Long id, boolean activo) {
        repo.findById(id).ifPresent(a -> { a.setActivo(activo); repo.save(a); });
    }

    private static void validar(String referencia, String descripcion, String codigoHs, String paisOrigen) {
        if (referencia == null || referencia.isBlank()) throw new DatoInvalidoException("El artículo necesita una referencia.");
        if (descripcion == null || descripcion.isBlank()) throw new DatoInvalidoException("El artículo " + referencia + " necesita descripción aduanera.");
        if (codigoHs == null || codigoHs.isBlank()) throw new DatoInvalidoException("El artículo " + referencia + " necesita código HS.");
        if (paisOrigen == null || paisOrigen.trim().length() != 2) throw new DatoInvalidoException("El país de origen de " + referencia + " es el código de dos letras (MA, ES).");
    }

    private static Article rellenar(Article a, String referencia, String descripcion, String codigoHs, String paisOrigen,
                                    Double pesoNeto, Double pesoBruto, BigDecimal valor) {
        a.setReferencia(referencia.trim());
        a.setDescripcionAduanera(descripcion.trim());
        a.setCodigoHs(codigoHs.trim());
        a.setPaisOrigen(paisOrigen.trim().toUpperCase());
        a.setPesoNetoUnitario(pesoNeto);
        a.setPesoBrutoUnitario(pesoBruto);
        a.setValorUnitario(valor);
        return a;
    }
}
