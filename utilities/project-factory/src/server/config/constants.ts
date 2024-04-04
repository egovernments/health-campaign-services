import Error from "./error.interface"

export const CONSTANTS:any={
    ERROR_CODES:{
   
        WORKS:{
            NO_MUSTER_ROLL_FOUND:"No Muster Roll Found for given Criteria",
            NO_CONTRACT_FOUND:"No Contract Found for given Criteria",
            NO_ESTIMATE_FOUND:"No Estimate Found for given Criteria",
            NO_MEASUREMENT_ROLL_FOUND:"No Measurement Found for given Criteria"
        }

    }

}

export const getErrorCodes = (module:string,key:string):Error=>{
    return {
        code:key,
        notFound:true,
        message:CONSTANTS.ERROR_CODES?.[module]?.[key]
    }
}