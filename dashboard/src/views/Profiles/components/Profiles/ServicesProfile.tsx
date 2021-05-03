import TextField from "@material-ui/core/TextField";
import React from "react";
import {ServicesTable, ServiceProfileType} from "./ServicesTable";

interface ServicesProfileParams {
  profileType: ServiceProfileType,
  newProfile: boolean,
  getProfile?: () => string | undefined,
  setProfile?: (profile: string) => void,
  doesProfileExist?: (profile: string) => boolean,
  getServices?: Array<string>,
  setServices?: (services: Array<string>) => void
}

const ServicesProfile = (params: ServicesProfileParams) => {
  const { profileType, newProfile, getProfile, setProfile, doesProfileExist, getServices, setServices } = params

  const profile = getProfile?.()

  return (<>
    <TextField
      autoFocus
      disabled={!newProfile}
      error={(newProfile && (!profile || (doesProfileExist?.(profile))))}
      fullWidth
      helperText={(newProfile && profile && doesProfileExist?.(profile)) ? 'Profile already exists': ''}
      label="Profile"
      margin="normal"
      onChange={(e: any) => setProfile?.(e.target.value)}
      required
      value={getProfile}
      variant="outlined"
    />
    <ServicesTable profileType={profileType} initialServices={getServices} setServices={setServices}/>
  </>)
}

export default ServicesProfile;
