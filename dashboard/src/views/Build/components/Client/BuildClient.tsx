import React, {useState} from 'react';

import {RouteComponentProps, useHistory} from "react-router-dom"

import { makeStyles } from '@material-ui/core/styles';
import Alert from "@material-ui/lab/Alert";
import {useTasksQuery} from "../../../../generated/graphql";

const useStyles = makeStyles(theme => ({
  root: {
    padding: theme.spacing(4)
  },
  alert: {
    marginTop: 25
  }
}));

interface BuildClientRouteParams {
}

interface BuildClientParams extends RouteComponentProps<BuildClientRouteParams> {
  fromUrl: string
}

const BuildClient = (props: BuildClientParams) => {
  const classes = useStyles()

  const history = useHistory()
  const [error, setError] = useState<string>()

  useTasksQuery({
    fetchPolicy: 'no-cache',
    variables: { taskType: 'BuildClientVersions', onlyActive: true },
    onCompleted(tasks) {
      tasks.tasks.length?
        history.push('client/monitor/'):
        history.push('client/start')
    },
    onError(err) { setError('Query client versions in process error ' + err.message) },
  })

  return (
    error?<Alert className={classes.alert} severity="error">{error}</Alert>:null
  )
}

export default BuildClient;
