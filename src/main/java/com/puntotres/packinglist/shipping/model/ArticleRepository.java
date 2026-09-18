package com.puntotres.packinglist.shipping.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleRepository extends JpaRepository<Article, Long> {
    Optional<Article> findByReferenciaIgnoreCase(String referencia);
    List<Article> findByActivoTrueOrderByReferenciaAsc();
    List<Article> findAllByOrderByReferenciaAsc();
}
