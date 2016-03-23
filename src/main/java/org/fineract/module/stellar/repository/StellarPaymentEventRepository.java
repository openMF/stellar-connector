package org.fineract.module.stellar.repository;

import org.fineract.module.stellar.persistencedomain.StellarPaymentEventPersistency;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StellarPaymentEventRepository extends
    CrudRepository<StellarPaymentEventPersistency, Long> {
}
