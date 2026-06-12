package com.plataforma.user.kyc.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.plataforma.shared.exception.UnauthorizedAccessException;
import com.plataforma.shared.exception.UserNotFoundException;
import com.plataforma.user.kyc.dto.KycInternalDto;
import com.plataforma.user.kyc.dto.KycStatusResponse;
import com.plataforma.user.kyc.model.KycDocument;
import com.plataforma.user.kyc.model.KycDocumentStatus;
import com.plataforma.user.kyc.repository.KycDocumentRepository;
import com.plataforma.user.model.KycStatus;
import com.plataforma.user.model.User;
import com.plataforma.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class KycServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private KycDocumentRepository documentRepository;
    @Mock private Storage storage;
    @Mock private com.plataforma.user.event.producer.UserContextEventProducer contextEventProducer;

    @InjectMocks private KycService kycService;

    private User user;

    @BeforeEach
    void setUp() {
        // El @Value("${gcp.storage.bucket}") no se inyecta via Mockito → forzar el valor
        ReflectionTestUtils.setField(kycService, "bucket", "test-bucket");

        user = User.builder()
                .id(42L)
                .email("test@user.com")
                .kycStatus(KycStatus.NOT_STARTED)
                .build();
    }

    // ── uploadDocuments ────────────────────────────────────────────────────

    @Test
    void uploadDocuments_subeArchivosYPasaUsuarioAPending() throws Exception {
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(documentRepository.findByUserIdOrderByUploadedAtDesc(42L)).thenReturn(List.of());

        MultipartFile file = new MockMultipartFile("files", "dni.pdf", "application/pdf", "dummy".getBytes());

        KycStatusResponse response = kycService.uploadDocuments(42L,
                List.of(file), List.of("DNI"));

        assertEquals(KycStatus.PENDING, user.getKycStatus());
        assertEquals(KycStatus.PENDING, response.getStatus());
        verify(storage, times(1)).createFrom(any(BlobInfo.class), any(java.io.InputStream.class));
        verify(documentRepository, times(1)).save(any(KycDocument.class));
    }

    @Test
    void uploadDocuments_sinArchivos_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> kycService.uploadDocuments(42L, List.of(), List.of()));
        verifyNoInteractions(userRepository, storage, documentRepository);
    }

    @Test
    void uploadDocuments_tiposNoMatchean_lanzaExcepcion() {
        MultipartFile f1 = new MockMultipartFile("f", "1.pdf", "application/pdf", "x".getBytes());
        MultipartFile f2 = new MockMultipartFile("f", "2.pdf", "application/pdf", "y".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> kycService.uploadDocuments(42L, List.of(f1, f2), List.of("DNI")));
        verifyNoInteractions(storage);
    }

    @Test
    void uploadDocuments_usuarioInexistente_lanzaUserNotFoundException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        MultipartFile file = new MockMultipartFile("files", "dni.pdf", "application/pdf", "x".getBytes());

        assertThrows(UserNotFoundException.class,
                () -> kycService.uploadDocuments(999L, List.of(file), List.of("DNI")));
        verifyNoInteractions(storage);
    }

    // ── getMyKycStatus ─────────────────────────────────────────────────────

    @Test
    void getMyKycStatus_devuelveEstadoYDocumentos() {
        user.setKycStatus(KycStatus.PENDING);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(documentRepository.findByUserIdOrderByUploadedAtDesc(42L)).thenReturn(List.of(
                KycDocument.builder().id(1L).documentType("DNI").status(KycDocumentStatus.PENDING).build()
        ));

        KycStatusResponse response = kycService.getMyKycStatus(42L);

        assertEquals(KycStatus.PENDING, response.getStatus());
        assertEquals(1, response.getDocuments().size());
        assertEquals("DNI", response.getDocuments().get(0).getDocumentType());
    }

    // ── getInternalStatus ──────────────────────────────────────────────────

    @Test
    void getInternalStatus_devuelveDtoMinimoParaServiciosInternos() {
        user.setKycStatus(KycStatus.APPROVED);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        KycInternalDto dto = kycService.getInternalStatus(42L);

        assertEquals(42L, dto.getUserId());
        assertEquals(KycStatus.APPROVED, dto.getStatus());
        verifyNoInteractions(documentRepository); // no carga documentos
    }

    // ── review ─────────────────────────────────────────────────────────────

    @Test
    void review_aprueba_actualizaUsuarioYDocumentos() {
        user.setKycStatus(KycStatus.PENDING);
        KycDocument doc1 = KycDocument.builder().id(1L).userId(42L)
                .documentType("DNI").status(KycDocumentStatus.PENDING).build();
        KycDocument doc2 = KycDocument.builder().id(2L).userId(42L)
                .documentType("FACTURA").status(KycDocumentStatus.PENDING).build();

        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(documentRepository.findByUserIdOrderByUploadedAtDesc(42L)).thenReturn(List.of(doc1, doc2));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        kycService.review(42L, KycStatus.APPROVED, null, 1L);

        assertEquals(KycStatus.APPROVED, user.getKycStatus());
        assertEquals(KycDocumentStatus.APPROVED, doc1.getStatus());
        assertEquals(KycDocumentStatus.APPROVED, doc2.getStatus());
        assertEquals(1L, doc1.getReviewedBy());
        assertNotNull(doc1.getReviewedAt());
    }

    @Test
    void review_rechaza_setteaReasonYActualizaDocumentos() {
        user.setKycStatus(KycStatus.PENDING);
        KycDocument doc = KycDocument.builder().id(1L).userId(42L)
                .documentType("DNI").status(KycDocumentStatus.PENDING).build();

        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(documentRepository.findByUserIdOrderByUploadedAtDesc(42L)).thenReturn(List.of(doc));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        kycService.review(42L, KycStatus.REJECTED, "DNI ilegible", 1L);

        assertEquals(KycStatus.REJECTED, user.getKycStatus());
        assertEquals(KycDocumentStatus.REJECTED, doc.getStatus());
        assertEquals("DNI ilegible", doc.getRejectionReason());
    }

    @Test
    void review_rechazoSinReason_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> kycService.review(42L, KycStatus.REJECTED, null, 1L));
    }

    @Test
    void review_decisionInvalida_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> kycService.review(42L, KycStatus.PENDING, null, 1L));
    }

    @Test
    void review_usuarioNoEstaPending_lanzaExcepcion() {
        user.setKycStatus(KycStatus.APPROVED);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        assertThrows(UnauthorizedAccessException.class,
                () -> kycService.review(42L, KycStatus.APPROVED, null, 1L));
    }
}
