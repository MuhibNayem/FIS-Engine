package com.bracit.fisprocess.dto.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetTransferRequestDto {
    private String newLocation;
    private String newCustodian;
    private String notes;
}