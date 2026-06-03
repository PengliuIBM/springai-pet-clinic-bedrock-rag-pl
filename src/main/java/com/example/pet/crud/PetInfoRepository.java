package com.example.pet.crud;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PetInfoRepository extends JpaRepository<PetInfo, Long> {
}
