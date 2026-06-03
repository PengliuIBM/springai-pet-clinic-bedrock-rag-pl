package com.example.pet.crud;

import com.example.pet.rag.PetRagService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/pets")
public class PetInfoController {

    private final PetInfoRepository repository;

    private final PetRagService ragService;

    public PetInfoController(PetInfoRepository repository, PetRagService ragService) {
        this.repository = repository;
        this.ragService = ragService;
    }

    @GetMapping
    public List<PetInfo> list() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public PetInfo get(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PetInfo create(@RequestBody PetInfo pet) {
        PetInfo saved = repository.save(pet);
        ragService.indexPet(saved);
        return saved;
    }

    @PutMapping("/{id}")
    public PetInfo update(@PathVariable Long id, @RequestBody PetInfo pet) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        pet.setId(id);
        PetInfo saved = repository.save(pet);
        ragService.indexPet(saved);
        return saved;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        ragService.removePet(id);
        repository.deleteById(id);
    }
}
