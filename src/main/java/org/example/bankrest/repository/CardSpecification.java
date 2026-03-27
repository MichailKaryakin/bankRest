package org.example.bankrest.repository;

import jakarta.persistence.criteria.JoinType;
import org.example.bankrest.entity.Card;
import org.example.bankrest.entity.CardStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class CardSpecification {

    private CardSpecification() {
    }

    public static Specification<Card> withFilters(Long ownerId, CardStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (ownerId != null) {
                predicates.add(cb.equal(root.get("owner").get("id"), ownerId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Card> fetchOwner() {
        return (root, query, cb) -> {
            Class<?> resultType = query.getResultType();
            if (resultType != Long.class && resultType != long.class) {
                root.fetch("owner", JoinType.LEFT);
                query.distinct(true);
            }
            return null;
        };
    }
}
