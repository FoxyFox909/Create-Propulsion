package com.deltasf.createpropulsion.registries;

import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.api.VsApi;

import com.deltasf.createpropulsion.balloons.hot_air.BalloonAttachment;
import com.deltasf.createpropulsion.balloons.serialization.BalloonSerializationHandler;
import com.deltasf.createpropulsion.impact_sensor.ImpactSensorAttachment;
import com.deltasf.createpropulsion.magnet.MagnetForceAttachment;
import com.deltasf.createpropulsion.propeller.PropellerAttachment;
import com.deltasf.createpropulsion.reaction_wheel.ReactionWheelAttachment;
import com.deltasf.createpropulsion.thruster.ThrusterForceAttachment;
import com.deltasf.createpropulsion.thruster.hoverpad.HoverpadAttachment;

public class PropulsionValkyrien {
    public static void init() {
        VsApi api = ValkyrienSkies.api();
        api.registerAttachment(api.newAttachmentRegistrationBuilder(ThrusterForceAttachment.class)
            .useLegacySerializer()
            .build()
        );
        api.registerAttachment(api.newAttachmentRegistrationBuilder(BalloonAttachment.class)
            .useLegacySerializer()
            .build()
        );
        api.registerAttachment(api.newAttachmentRegistrationBuilder(MagnetForceAttachment.class)
            .useLegacySerializer()
            .build()
        );
        api.registerAttachment(api.newAttachmentRegistrationBuilder(PropellerAttachment.class)
            .useLegacySerializer()
            .build()
        );
        api.registerAttachment(api.newAttachmentRegistrationBuilder(ReactionWheelAttachment.class)
            .useLegacySerializer()
            .build()
        );
        api.registerAttachment(api.newAttachmentRegistrationBuilder(ImpactSensorAttachment.class)
            .useLegacySerializer()
            .build()
        );
        //Hoverpad appliers are rebuilt by their block entities on load, nothing to save
        api.registerAttachment(api.newAttachmentRegistrationBuilder(HoverpadAttachment.class)
            .useTransientSerializer()
            .build()
        );

        //Query ships for deserialization with balloons
        ValkyrienSkies.api().getShipLoadEvent().on((event) -> {
            //time to commit a war crime
            LoadedServerShip ship = event.getShip();
            if (ship.getAttachment(BalloonAttachment.class) != null) {
                BalloonSerializationHandler.queryShipLoad(ship);
            }
        });
    }
}
