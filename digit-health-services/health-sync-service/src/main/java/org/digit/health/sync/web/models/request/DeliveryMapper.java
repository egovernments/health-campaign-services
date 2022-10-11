package org.digit.health.sync.web.models.request;

import org.digit.health.sync.web.models.Delivery;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface DeliveryMapper {

    DeliveryMapper INSTANCE = Mappers.getMapper(DeliveryMapper.class);

    DeliveryRequest toRequest(Delivery delivery);
}
